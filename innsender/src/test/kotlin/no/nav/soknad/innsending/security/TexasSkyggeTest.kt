package no.nav.soknad.innsending.security

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.soknad.innsending.consumerapis.texas.TexasIntrospeksjon
import no.nav.soknad.innsending.consumerapis.texas.TexasIntrospeksjonKlient
import no.nav.soknad.innsending.consumerapis.texas.TexasIntrospeksjonKlient.Companion.ENTRA_ID
import no.nav.soknad.innsending.consumerapis.texas.TexasIntrospeksjonKlient.Companion.TOKENX
import no.nav.soknad.innsending.consumerapis.texas.TexasUnavailableException
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TexasSkyggeTest {

	private val token = "eyJhbGciOiJSUzI1NiJ9.innkommende-token.signatur"
	private val pid = "12345678901"

	private val klient = mockk<TexasIntrospeksjonKlient>()
	private val metrics = InnsenderMetrics(PrometheusRegistry())
	private val sammeTråd = Executor { it.run() }
	private val skygge = TexasSkygge(klient, metrics, 1.0, sammeTråd)

	private val logger = LoggerFactory.getLogger(TexasSkygge::class.java) as Logger
	private val logg = ListAppender<ILoggingEvent>()

	@BeforeEach
	fun startLogg() {
		logg.start()
		logger.addAppender(logg)
	}

	@AfterEach
	fun stoppLogg() {
		logger.detachAppender(logg)
	}

	private fun tokenX(vararg claims: Pair<String, Any?>) = TokenAutentisering(TokenUtsteder.TOKENX, mapOf(*claims), token)
	private fun aktiv(vararg claims: Pair<String, Any?>) = TexasIntrospeksjon(true, mapOf(*claims))
	private val inaktiv = TexasIntrospeksjon(false, emptyMap())

	private fun antall(issuer: String, outcome: String) = metrics.getTexasSkygge(issuer, outcome)

	@Test
	fun `like svar telles som samsvar`() {
		every { klient.introspekter(TOKENX, token) } returns aktiv("pid" to pid, "acr" to "Level4")

		skygge.sammenlign(token, tokenX("pid" to pid, "acr" to "Level4"))

		assertEquals(1.0, antall("tokenx", "samsvar"))
	}

	@Test
	fun `Texas avviser token som token-support godtok`() {
		every { klient.introspekter(TOKENX, token) } returns inaktiv

		skygge.sammenlign(token, tokenX("pid" to pid, "acr" to "Level4"))

		assertEquals(1.0, antall("tokenx", "texas_avviser"))
	}

	@Test
	fun `ulike tilganger telles og logges med navn på tilganger`() {
		every { klient.introspekter(TOKENX, token) } returns aktiv("pid" to pid, "acr" to "idporten-loa-substantial")

		skygge.sammenlign(token, tokenX("pid" to pid, "acr" to "Level4"))

		assertEquals(1.0, antall("tokenx", "ulike_tilganger"))
	}

	@Test
	fun `ulik identitet telles uten at verdiene logges`() {
		every { klient.introspekter(TOKENX, token) } returns aktiv("pid" to "10987654321", "acr" to "Level4")

		skygge.sammenlign(token, tokenX("pid" to pid, "acr" to "Level4"))

		assertEquals(1.0, antall("tokenx", "ulik_identitet"))
		assertFalse(logg.list.any { it.formattedMessage.contains(pid) || it.formattedMessage.contains("10987654321") })
	}

	@Test
	fun `token-support avviste og Texas avviser for begge utstedere`() {
		every { klient.introspekter(any(), token) } returns inaktiv

		skygge.sammenlign(token, null)

		assertEquals(1.0, antall("ingen", "samsvar"))
		verify { klient.introspekter(TOKENX, token) }
		verify { klient.introspekter(ENTRA_ID, token) }
	}

	@Test
	fun `Texas godtar token som token-support avviste`() {
		every { klient.introspekter(TOKENX, token) } returns inaktiv
		every { klient.introspekter(ENTRA_ID, token) } returns aktiv("azp" to "klient")

		skygge.sammenlign(token, null)

		assertEquals(1.0, antall("azure", "texas_godtar"))
	}

	@Test
	fun `feil mot Texas telles og påvirker ikke den som kaller`() {
		every { klient.introspekter(TOKENX, token) } throws TexasUnavailableException("Texas svarte 503 på introspeksjon")

		skygge.sammenlign(token, tokenX("pid" to pid, "acr" to "Level4"))

		assertEquals(1.0, antall("tokenx", "texas_feil"))
	}

	@Test
	fun `full kø hoppes over`() {
		val fullKø = Executor { throw RejectedExecutionException() }

		TexasSkygge(klient, metrics, 1.0, fullKø).sammenlign(token, tokenX("acr" to "Level4"))

		assertEquals(1.0, antall("tokenx", "hoppet_over"))
	}

	@Test
	fun `sammenligning som har ventet for lenge i køen telles som for gammel`() {
		var nå = 0L
		val køen = mutableListOf<Runnable>()
		val skyggeMedKø = TexasSkygge(klient, metrics, 1.0, { køen += it }, { nå })

		skyggeMedKø.sammenlign(token, tokenX("pid" to pid, "acr" to "Level4"))
		nå += java.util.concurrent.TimeUnit.SECONDS.toNanos(6)
		køen.single().run()

		assertEquals(1.0, antall("tokenx", "for_gammel"))
		verify(exactly = 0) { klient.introspekter(any(), any()) }
	}

	@Test
	fun `token som har utløpt før Texas validerer er ikke et avvik`() {
		every { klient.introspekter(TOKENX, token) } returns inaktiv

		skygge.sammenlign(token, tokenX("pid" to pid, "acr" to "Level4", "exp" to java.util.Date(System.currentTimeMillis() - 1000)))

		assertEquals(1.0, antall("tokenx", "for_gammel"))
	}

	@Test
	fun `flere token i samme forespørsel sammenlignes ikke`() {
		val begge = TokenAutentisering(
			mapOf(
				TokenUtsteder.TOKENX to ValidertToken(mapOf("acr" to "Level4"), token),
				TokenUtsteder.AZURE to ValidertToken(emptyMap(), "azure"),
			)
		)

		skygge.sammenlign(token, begge)

		assertEquals(1.0, antall("ingen", "flere_token"))
		verify(exactly = 0) { klient.introspekter(any(), any()) }
	}

	@Test
	fun `andel 0 sammenligner ingenting`() {
		TexasSkygge(klient, metrics, 0.0, sammeTråd).sammenlign(token, tokenX("acr" to "Level4"))

		verify(exactly = 0) { klient.introspekter(any(), any()) }
	}

	@Test
	fun `loggen inneholder aldri tokenet`() {
		every { klient.introspekter(any(), token) } returns inaktiv
		skygge.sammenlign(token, tokenX("pid" to pid, "acr" to "Level4"))
		every { klient.introspekter(any(), token) } throws TexasUnavailableException("Fikk ikke kontakt med Texas")
		skygge.sammenlign(token, null)

		assertFalse(logg.list.any { it.formattedMessage.contains(token) })
	}
}
