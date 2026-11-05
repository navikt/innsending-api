package no.nav.soknad.innsending.security

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.soknad.innsending.consumerapis.token.IntrospectionResult
import no.nav.soknad.innsending.consumerapis.token.TokenIntrospector
import no.nav.soknad.innsending.consumerapis.token.TokenServiceUnavailableException
import no.nav.soknad.innsending.security.TokenIssuer.AZURE
import no.nav.soknad.innsending.security.TokenIssuer.TOKENX
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TexasShadowTest {

	// Usignert, men med iss, slik at skyggemodus sender det til Texas. Klienten er mocket
	private val token = com.nimbusds.jwt.PlainJWT(com.nimbusds.jwt.JWTClaimsSet.Builder().issuer("https://tokenx.test").build()).serialize()
	private val pid = "12345678901"

	private val introspector = mockk<TokenIntrospector>()
	private val metrics = InnsenderMetrics(PrometheusRegistry())
	private val sameThread = Executor { it.run() }
	private val shadow = TexasShadow(introspector, metrics, 1.0, sameThread)

	private val logger = LoggerFactory.getLogger(TexasShadow::class.java) as Logger
	private val logAppender = ListAppender<ILoggingEvent>()

	@BeforeEach
	fun startLog() {
		logAppender.start()
		logger.addAppender(logAppender)
	}

	@AfterEach
	fun stopLog() {
		logger.detachAppender(logAppender)
	}

	private fun tokenX(vararg claims: Pair<String, Any?>) = TokenAuthentication(TokenIssuer.TOKENX, mapOf(*claims), token)
	private fun active(vararg claims: Pair<String, Any?>) = IntrospectionResult(true, mapOf(*claims))
	private val inactive = IntrospectionResult(false, emptyMap())

	private fun countOf(issuer: String, outcome: String) = metrics.getTexasShadow(issuer, outcome)

	@Test
	fun `equal answers are counted as a match`() {
		every { introspector.introspect(TOKENX, token) } returns active("pid" to pid, "acr" to "Level4")

		shadow.compare(token, tokenX("pid" to pid, "acr" to "Level4"))

		assertEquals(1.0, countOf("tokenx", "match"))
	}

	@Test
	fun `Texas rejects a token that token-support accepted`() {
		every { introspector.introspect(TOKENX, token) } returns inactive

		shadow.compare(token, tokenX("pid" to pid, "acr" to "Level4"))

		assertEquals(1.0, countOf("tokenx", "texas_rejects"))
	}

	@Test
	fun `different authorities are counted and logged with authority names`() {
		every { introspector.introspect(TOKENX, token) } returns active("pid" to pid, "acr" to "idporten-loa-substantial")

		shadow.compare(token, tokenX("pid" to pid, "acr" to "Level4"))

		assertEquals(1.0, countOf("tokenx", "different_authorities"))
	}

	@Test
	fun `a different identity is counted without logging the values`() {
		every { introspector.introspect(TOKENX, token) } returns active("pid" to "10987654321", "acr" to "Level4")

		shadow.compare(token, tokenX("pid" to pid, "acr" to "Level4"))

		assertEquals(1.0, countOf("tokenx", "different_identity"))
		assertFalse(logAppender.list.any { it.formattedMessage.contains(pid) || it.formattedMessage.contains("10987654321") })
	}

	@Test
	fun `token-support rejected and Texas rejects for both issuers`() {
		every { introspector.introspect(any(), token) } returns inactive

		shadow.compare(token, null)

		assertEquals(1.0, countOf("none", "match"))
		verify { introspector.introspect(TOKENX, token) }
		verify { introspector.introspect(AZURE, token) }
	}

	@Test
	fun `rejected junk without iss is not sent to Texas`() {
		listOf("ikke-en-jwt", com.nimbusds.jwt.PlainJWT(com.nimbusds.jwt.JWTClaimsSet.Builder().build()).serialize())
			.forEach { shadow.compare(it, null) }

		verify(exactly = 0) { introspector.introspect(any(), any()) }
	}

	@Test
	fun `Texas accepts a token that token-support rejected`() {
		every { introspector.introspect(TOKENX, token) } returns inactive
		every { introspector.introspect(AZURE, token) } returns active("azp" to "klient")

		shadow.compare(token, null)

		assertEquals(1.0, countOf("azure", "texas_accepts"))
	}

	@Test
	fun `a Texas failure is counted and does not affect the caller`() {
		every { introspector.introspect(TOKENX, token) } throws TokenServiceUnavailableException("Texas svarte 503 på introspeksjon")

		shadow.compare(token, tokenX("pid" to pid, "acr" to "Level4"))

		assertEquals(1.0, countOf("tokenx", "texas_error"))
	}

	@Test
	fun `a full queue is skipped`() {
		val fullQueue = Executor { throw RejectedExecutionException() }

		TexasShadow(introspector, metrics, 1.0, fullQueue).compare(token, tokenX("acr" to "Level4"))

		assertEquals(1.0, countOf("tokenx", "skipped"))
	}

	@Test
	fun `a comparison that waited too long in the queue is counted as too old`() {
		var now = 0L
		val queue = mutableListOf<Runnable>()
		val shadowWithQueue = TexasShadow(introspector, metrics, 1.0, { queue += it }, { now })

		shadowWithQueue.compare(token, tokenX("pid" to pid, "acr" to "Level4"))
		now += java.util.concurrent.TimeUnit.SECONDS.toNanos(6)
		queue.single().run()

		assertEquals(1.0, countOf("tokenx", "too_old"))
		verify(exactly = 0) { introspector.introspect(any(), any()) }
	}

	@Test
	fun `a token that expired before Texas validated it is not a mismatch`() {
		every { introspector.introspect(TOKENX, token) } returns inactive

		shadow.compare(token, tokenX("pid" to pid, "acr" to "Level4", "exp" to java.util.Date(System.currentTimeMillis() - 1000)))

		assertEquals(1.0, countOf("tokenx", "too_old"))
	}

	@Test
	fun `several tokens in one request are not compared`() {
		val both = TokenAuthentication(
			mapOf(
				TokenIssuer.TOKENX to ValidatedToken(mapOf("acr" to "Level4"), token),
				TokenIssuer.AZURE to ValidatedToken(emptyMap(), "azure"),
			)
		)

		shadow.compare(token, both)

		assertEquals(1.0, countOf("none", "multiple_tokens"))
		verify(exactly = 0) { introspector.introspect(any(), any()) }
	}

	@Test
	fun `sample rate 0 compares nothing`() {
		TexasShadow(introspector, metrics, 0.0, sameThread).compare(token, tokenX("acr" to "Level4"))

		verify(exactly = 0) { introspector.introspect(any(), any()) }
	}

	@Test
	fun `the log never contains the token`() {
		every { introspector.introspect(any(), token) } returns inactive
		shadow.compare(token, tokenX("pid" to pid, "acr" to "Level4"))
		every { introspector.introspect(any(), token) } throws TokenServiceUnavailableException("Fikk ikke kontakt med Texas")
		shadow.compare(token, null)

		assertFalse(logAppender.list.any { it.formattedMessage.contains(token) })
	}
}
