package no.nav.soknad.innsending.security

import no.nav.soknad.innsending.consumerapis.texas.TexasIntrospeksjonKlient
import no.nav.soknad.innsending.consumerapis.texas.TexasUnavailableException
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Skyggemodus (#461, steg 3h): Texas validerer det innkommende tokenet ved siden av token-support, og vi teller om
 * svarene er like. Svaret til den som kaller påvirkes aldri. Fjernes i steg 4.
 *
 * Sammenligningen kjører i egne tråder, så den ikke øker svartiden. Er køen full, hoppes forespørselen over. Har en
 * sammenligning ventet for lenge i køen, eller har tokenet utløpt underveis, telles den som for gammel i stedet for
 * som avvik, fordi token-support og Texas da ikke validerte på samme tidspunkt.
 * Metrikken og loggen har bare utsteder, utfall og navn på tilganger, aldri token, claimverdier, identitet eller
 * feilteksten fra Texas.
 */
class TexasSkygge(
	private val klient: TexasIntrospeksjonKlient,
	private val metrics: InnsenderMetrics,
	private val andel: Double,
	private val executor: Executor = standardExecutor(),
	private val klokke: () -> Long = System::nanoTime,
) : DisposableBean {

	private val logger = LoggerFactory.getLogger(javaClass)

	enum class Utfall(val label: String) {
		SAMSVAR("samsvar"),
		TEXAS_AVVISER("texas_avviser"),
		TEXAS_GODTAR("texas_godtar"),
		ULIKE_TILGANGER("ulike_tilganger"),
		ULIK_IDENTITET("ulik_identitet"),
		TEXAS_FEIL("texas_feil"),
		HOPPET_OVER("hoppet_over"),
		FOR_GAMMEL("for_gammel"),
		FLERE_TOKEN("flere_token"),
	}

	/** Kalles i forespørselens tråd. Kaster aldri. */
	fun sammenlign(token: String, tokenSupport: TokenAutentisering?) {
		if (andel < 1.0 && ThreadLocalRandom.current().nextDouble() >= andel) return
		val utsteder = tokenSupport?.tokens?.keys?.singleOrNull()
		if (tokenSupport != null && utsteder == null) {
			// Flere token i samme header. Texas validerer ett token om gangen, så dette sammenlignes ikke
			tell(null, Utfall.FLERE_TOKEN)
			return
		}
		val lagtIKø = klokke()
		try {
			executor.execute { sammenlignNå(token, tokenSupport, lagtIKø) }
		} catch (_: RejectedExecutionException) {
			tell(utsteder, Utfall.HOPPET_OVER)
		}
	}

	/** Kalles når Authorization-headeren har flere kommaseparerte token. */
	fun flereToken() = tell(null, Utfall.FLERE_TOKEN)

	internal fun sammenlignNå(token: String, tokenSupport: TokenAutentisering?, lagtIKø: Long = klokke()) {
		val utsteder = tokenSupport?.tokens?.keys?.singleOrNull()
		if (klokke() - lagtIKø > MAKS_KØALDER_NANOS) {
			tell(utsteder, Utfall.FOR_GAMMEL)
			return
		}
		try {
			if (tokenSupport != null && utsteder != null) {
				sammenlignGodkjent(token, utsteder, tokenSupport.token(utsteder)!!)
			} else {
				sammenlignAvvist(token)
			}
		} catch (e: TexasUnavailableException) {
			// Meldingen lager vi selv, uten respons-body eller token
			logger.warn("Texas-skygge: ${e.message}")
			tell(utsteder, Utfall.TEXAS_FEIL)
		} catch (e: RuntimeException) {
			logger.warn("Texas-skygge: uventet feil (${e.javaClass.simpleName})")
			tell(utsteder, Utfall.TEXAS_FEIL)
		}
	}

	private fun sammenlignGodkjent(token: String, utsteder: TokenUtsteder, tokenSupport: ValidertToken) {
		val texas = klient.introspekter(identityProvider(utsteder), token)
		if (!texas.aktiv) {
			if (utløpt(tokenSupport)) {
				tell(utsteder, Utfall.FOR_GAMMEL)
				return
			}
			// Feilteksten fra Texas logges ikke, fordi vi ikke kontrollerer hva den inneholder
			logger.warn("Texas-skygge: token-support godtok $utsteder-token som Texas avviste")
			tell(utsteder, Utfall.TEXAS_AVVISER)
			return
		}

		val tilgangerTokenSupport = Tilgang.tilgangerFor(utsteder, tokenSupport.claims).toSortedSet()
		val tilgangerTexas = Tilgang.tilgangerFor(utsteder, texas.claims).toSortedSet()
		if (tilgangerTokenSupport != tilgangerTexas) {
			logger.warn("Texas-skygge: ulike tilganger for $utsteder-token, token-support=$tilgangerTokenSupport texas=$tilgangerTexas")
			tell(utsteder, Utfall.ULIKE_TILGANGER)
			return
		}

		val uliktIdentitetsclaim = IDENTITETSCLAIMS.filter { tokenSupport.claims[it] != texas.claims[it] }
		if (uliktIdentitetsclaim.isNotEmpty()) {
			// Bare navnene på claimene, aldri verdiene
			logger.warn("Texas-skygge: ulike identitetsclaims for $utsteder-token: $uliktIdentitetsclaim")
			tell(utsteder, Utfall.ULIK_IDENTITET)
			return
		}

		tell(utsteder, Utfall.SAMSVAR)
	}

	// Token-support avviste tokenet, eller det var ikke et token den kjente. Texas skal avvise det for begge utstedere
	private fun sammenlignAvvist(token: String) {
		val godtattAv = TokenUtsteder.entries.firstOrNull { klient.introspekter(identityProvider(it), token).aktiv }
		if (godtattAv != null) {
			logger.warn("Texas-skygge: Texas godtok $godtattAv-token som token-support avviste")
			tell(godtattAv, Utfall.TEXAS_GODTAR)
		} else {
			tell(null, Utfall.SAMSVAR)
		}
	}

	// Token-support gir exp som Date. Texas validerer senere, så et token kan ha utløpt i mellomtiden
	private fun utløpt(token: ValidertToken): Boolean =
		(token.claims["exp"] as? java.util.Date)?.before(java.util.Date()) ?: false

	private fun tell(utsteder: TokenUtsteder?, utfall: Utfall) =
		metrics.incTexasSkygge(utsteder?.name?.lowercase() ?: "ingen", utfall.label)

	private fun identityProvider(utsteder: TokenUtsteder) = when (utsteder) {
		TokenUtsteder.TOKENX -> TexasIntrospeksjonKlient.TOKENX
		TokenUtsteder.AZURE -> TexasIntrospeksjonKlient.ENTRA_ID
	}

	override fun destroy() {
		(executor as? ThreadPoolExecutor)?.shutdownNow()
	}

	companion object {
		// Claimene appen leser identitet fra, se SpringSecuritySubjectHandler
		private val IDENTITETSCLAIMS = listOf("pid", "sub", "client_id", "azp", "NAVident")
		private val MAKS_KØALDER_NANOS = TimeUnit.SECONDS.toNanos(5)

		private fun standardExecutor() = ThreadPoolExecutor(
			2, 2, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(200),
			{ runnable -> Thread(runnable, "texas-skygge").apply { isDaemon = true } },
			ThreadPoolExecutor.AbortPolicy(),
		)
	}
}
