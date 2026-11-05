package no.nav.soknad.innsending.security

import com.nimbusds.jwt.JWTParser
import no.nav.soknad.innsending.consumerapis.token.TokenIntrospector
import no.nav.soknad.innsending.consumerapis.token.TokenServiceUnavailableException
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
class TexasShadow(
	private val introspector: TokenIntrospector,
	private val metrics: InnsenderMetrics,
	private val sampleRate: Double,
	private val executor: Executor = defaultExecutor(),
	private val clock: () -> Long = System::nanoTime,
) : DisposableBean {

	private val logger = LoggerFactory.getLogger(javaClass)

	enum class Outcome(val label: String) {
		MATCH("match"),
		TEXAS_REJECTS("texas_rejects"),
		TEXAS_ACCEPTS("texas_accepts"),
		DIFFERENT_AUTHORITIES("different_authorities"),
		DIFFERENT_IDENTITY("different_identity"),
		TEXAS_ERROR("texas_error"),
		SKIPPED("skipped"),
		TOO_OLD("too_old"),
		MULTIPLE_TOKENS("multiple_tokens"),
	}

	/** Kalles i forespørselens tråd. Kaster aldri. */
	fun compare(token: String, tokenSupport: TokenAuthentication?) {
		if (sampleRate < 1.0 && ThreadLocalRandom.current().nextDouble() >= sampleRate) return
		val issuer = tokenSupport?.tokens?.keys?.singleOrNull()
		if (tokenSupport != null && issuer == null) {
			// Flere token i samme header. Texas validerer ett token om gangen, så dette sammenlignes ikke
			count(null, Outcome.MULTIPLE_TOKENS)
			return
		}
		val enqueuedAt = clock()
		try {
			executor.execute { compareNow(token, tokenSupport, enqueuedAt) }
		} catch (_: RejectedExecutionException) {
			count(issuer, Outcome.SKIPPED)
		}
	}

	/** Kalles når Authorization-headeren har flere kommaseparerte token. */
	fun multipleTokens() = count(null, Outcome.MULTIPLE_TOKENS)

	internal fun compareNow(token: String, tokenSupport: TokenAuthentication?, enqueuedAt: Long = clock()) {
		val issuer = tokenSupport?.tokens?.keys?.singleOrNull()
		if (clock() - enqueuedAt > MAX_QUEUE_AGE_NANOS) {
			count(issuer, Outcome.TOO_OLD)
			return
		}
		try {
			if (tokenSupport != null && issuer != null) {
				compareAccepted(token, issuer, tokenSupport.token(issuer)!!)
			} else {
				compareRejected(token)
			}
		} catch (e: TokenServiceUnavailableException) {
			// Meldingen lager vi selv, uten respons-body eller token
			logger.warn("Texas-skygge: ${e.message}")
			count(issuer, Outcome.TEXAS_ERROR)
		} catch (e: RuntimeException) {
			logger.warn("Texas-skygge: uventet feil (${e.javaClass.simpleName})")
			count(issuer, Outcome.TEXAS_ERROR)
		}
	}

	private fun compareAccepted(token: String, issuer: TokenIssuer, tokenSupport: ValidatedToken) {
		val texas = introspector.introspect(issuer, token)
		if (!texas.active) {
			if (isExpired(tokenSupport)) {
				count(issuer, Outcome.TOO_OLD)
				return
			}
			// Feilteksten fra Texas logges ikke, fordi vi ikke kontrollerer hva den inneholder
			logger.warn("Texas-skygge: token-support godtok $issuer-token som Texas avviste")
			count(issuer, Outcome.TEXAS_REJECTS)
			return
		}

		val tokenSupportAuthorities = Access.authoritiesFor(issuer, tokenSupport.claims).toSortedSet()
		val texasAuthorities = Access.authoritiesFor(issuer, texas.claims).toSortedSet()
		if (tokenSupportAuthorities != texasAuthorities) {
			logger.warn("Texas-skygge: ulike tilganger for $issuer-token, token-support=$tokenSupportAuthorities texas=$texasAuthorities")
			count(issuer, Outcome.DIFFERENT_AUTHORITIES)
			return
		}

		val differentIdentityClaims = IDENTITY_CLAIMS.filter { tokenSupport.claims[it] != texas.claims[it] }
		if (differentIdentityClaims.isNotEmpty()) {
			// Bare navnene på claimene, aldri verdiene
			logger.warn("Texas-skygge: ulike identitetsclaims for $issuer-token: $differentIdentityClaims")
			count(issuer, Outcome.DIFFERENT_IDENTITY)
			return
		}

		count(issuer, Outcome.MATCH)
	}

	// Token-support avviste tokenet, eller det var ikke et token den kjente. Texas skal avvise det for begge utstedere
	private fun compareRejected(token: String) {
		// Bare token som ser ut som JWT med iss sendes til Texas, så søppel i Authorization ikke gir Texas-trafikk
		if (runCatching { JWTParser.parse(token).jwtClaimsSet.issuer }.getOrNull() == null) {
			count(null, Outcome.MATCH)
			return
		}
		val acceptedBy = TokenIssuer.entries.firstOrNull { introspector.introspect(it, token).active }
		if (acceptedBy != null) {
			logger.warn("Texas-skygge: Texas godtok $acceptedBy-token som token-support avviste")
			count(acceptedBy, Outcome.TEXAS_ACCEPTS)
		} else {
			count(null, Outcome.MATCH)
		}
	}

	// Token-support gir exp som Date. Texas validerer senere, så et token kan ha utløpt i mellomtiden
	private fun isExpired(token: ValidatedToken): Boolean =
		(token.claims["exp"] as? java.util.Date)?.before(java.util.Date()) ?: false

	private fun count(issuer: TokenIssuer?, outcome: Outcome) =
		metrics.incTexasShadow(issuer?.name?.lowercase() ?: "none", outcome.label)


	override fun destroy() {
		(executor as? ThreadPoolExecutor)?.shutdownNow()
	}

	companion object {
		// Claimene appen leser identitet fra, se SpringSecuritySubjectHandler
		private val IDENTITY_CLAIMS = listOf("pid", "sub", "client_id", "azp", "NAVident")
		private val MAX_QUEUE_AGE_NANOS = TimeUnit.SECONDS.toNanos(5)

		private fun defaultExecutor() = ThreadPoolExecutor(
			2, 2, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(200),
			{ runnable -> Thread(runnable, "texas-shadow").apply { isDaemon = true } },
			ThreadPoolExecutor.AbortPolicy(),
		)
	}
}
