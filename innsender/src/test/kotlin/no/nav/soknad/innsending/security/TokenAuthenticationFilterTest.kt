package no.nav.soknad.innsending.security

import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.soknad.innsending.consumerapis.token.IntrospectionResult
import no.nav.soknad.innsending.consumerapis.token.TokenIntrospector
import no.nav.soknad.innsending.consumerapis.token.TokenServiceUnavailableException
import no.nav.soknad.innsending.security.TokenIssuer.AZURE
import no.nav.soknad.innsending.security.TokenIssuer.TOKENX
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class TokenAuthenticationFilterTest {

	private val tokenxIssuer = "https://tokenx.test"
	private val azureIssuer = "https://login.test/tenant/v2.0"
	private val issuerUrls = mapOf(TokenIssuer.TOKENX to tokenxIssuer, TokenIssuer.AZURE to azureIssuer)

	private val introspector = mockk<TokenIntrospector>()

	@AfterEach
	fun cleanUp() = SecurityContextHolder.clearContext()

	// Usignert, fordi filteret bare leser iss for å velge utsteder. Introspeksjonen er mocket
	private fun token(issuer: String?) = PlainJWT(JWTClaimsSet.Builder().issuer(issuer).build()).serialize()

	private fun runFilter(authorization: String?): Pair<MockHttpServletRequest, Authentication?> {
		val request = MockHttpServletRequest().apply { authorization?.let { addHeader("Authorization", it) } }
		var captured: Authentication? = null
		TokenAuthenticationFilter(introspector, issuerUrls)
			.doFilter(request, MockHttpServletResponse()) { _, _ -> captured = SecurityContextHolder.getContext().authentication }
		return request to captured
	}

	@Test
	fun `an Azure token is validated only as Azure, with one call`() {
		val azure = token(azureIssuer)
		every { introspector.introspect(AZURE, azure) } returns
			IntrospectionResult(true, mapOf("azp" to "klient", "roles" to listOf("nologin-access")))

		val authentication = runFilter("Bearer $azure").second as TokenAuthentication

		assertEquals(setOf(TokenIssuer.AZURE), authentication.tokens.keys)
		assertEquals(setOf(Access.AZURE, Access.AZURE_NOLOGIN), authentication.authorities.map { it.authority }.toSet())
		assertEquals(azure, authentication.credentials)
		verify(exactly = 1) { introspector.introspect(any(), any()) }
	}

	@Test
	fun `a TokenX token is validated only as TokenX, with one call`() {
		val tokenx = token(tokenxIssuer)
		every { introspector.introspect(TOKENX, tokenx) } returns IntrospectionResult(true, mapOf("pid" to "12345678901", "acr" to "Level4"))

		val authentication = runFilter("Bearer $tokenx").second as TokenAuthentication

		assertEquals(setOf(TokenIssuer.TOKENX), authentication.tokens.keys)
		verify(exactly = 1) { introspector.introspect(any(), any()) }
	}

	@Test
	fun `Texas down for Azure does not block TokenX`() {
		val tokenx = token(tokenxIssuer)
		every { introspector.introspect(AZURE, any()) } throws TokenServiceUnavailableException("Texas svarte 503 på introspeksjon")
		every { introspector.introspect(TOKENX, tokenx) } returns IntrospectionResult(true, mapOf("acr" to "Level4"))

		val (request, authentication) = runFilter("Bearer $tokenx")

		assertEquals(setOf(TokenIssuer.TOKENX), (authentication as TokenAuthentication).tokens.keys)
		assertNull(request.getAttribute(TokenAuthenticationFilter.TOKEN_VALIDATION_UNAVAILABLE))
	}

	@Test
	fun `an unknown, missing or unreadable iss gives no authentication and no call`() {
		listOf(token("https://ukjent.test"), token(null), "ikke-en-jwt").forEach { assertNull(runFilter("Bearer $it").second) }

		verify(exactly = 0) { introspector.introspect(any(), any()) }
	}

	@Test
	fun `an invalid token gives no authentication`() {
		every { introspector.introspect(AZURE, any()) } returns IntrospectionResult(false, emptyMap())

		assertNull(runFilter("Bearer ${token(azureIssuer)}").second)
	}

	@Test
	fun `Texas down marks the request and lets it continue`() {
		every { introspector.introspect(any(), any()) } throws TokenServiceUnavailableException("Texas svarte 503 på introspeksjon")

		val (request, authentication) = runFilter("Bearer ${token(azureIssuer)}")

		assertNull(authentication)
		assertEquals(true, request.getAttribute(TokenAuthenticationFilter.TOKEN_VALIDATION_UNAVAILABLE))
	}

	@Test
	fun `an unexpected validation error is treated as a Texas error`() {
		every { introspector.introspect(any(), any()) } throws IllegalStateException("uventet")

		val (request, authentication) = runFilter("Bearer ${token(azureIssuer)}")

		assertNull(authentication)
		assertEquals(true, request.getAttribute(TokenAuthenticationFilter.TOKEN_VALIDATION_UNAVAILABLE))
	}

	@Test
	fun `existing authentication is not overwritten and not sent to Texas`() {
		val existing = TokenAuthentication(TokenIssuer.TOKENX, mapOf("acr" to "Level4"), "tokenx-token")
		SecurityContextHolder.getContext().authentication = existing

		assertSame(existing, runFilter("Bearer ${token(tokenxIssuer)}").second)
		verify(exactly = 0) { introspector.introspect(any(), any()) }
	}

	@Test
	fun `Texas is not asked without a token or with several tokens`() {
		listOf(null, "Basic abc", "Bearer a,b").forEach { assertNull(runFilter(it).second) }

		verify(exactly = 0) { introspector.introspect(any(), any()) }
	}

	@Test
	fun `an issuer without a configured issuer URL stops startup`() {
		assertFailsWith<IllegalArgumentException> {
			TokenAuthenticationFilter(introspector, mapOf(TokenIssuer.AZURE to azureIssuer))
		}
	}

	private fun check(request: MockHttpServletRequest) = request.getAttribute(TokenCheck.ATTRIBUTE) as TokenCheck?

	@Test
	fun `the result is recorded for each outcome`() {
		val valid = token(tokenxIssuer)
		val expired = token(azureIssuer)
		every { introspector.introspect(TOKENX, valid) } returns IntrospectionResult(true, mapOf("acr" to "Level4"))
		every { introspector.introspect(AZURE, expired) } returns IntrospectionResult(false, emptyMap())

		val cases = mapOf(
			null to TokenCheck(TokenValidationResult.MISSING, null),
			"Basic abc" to TokenCheck(TokenValidationResult.MISSING, null),
			"Bearer a,b" to TokenCheck(TokenValidationResult.MALFORMED, null),
			"Bearer ikke-en-jwt" to TokenCheck(TokenValidationResult.MALFORMED, null),
			"Bearer ${token("https://ukjent.test")}" to TokenCheck(TokenValidationResult.UNKNOWN_ISSUER, null),
			"Bearer ${token(null)}" to TokenCheck(TokenValidationResult.UNKNOWN_ISSUER, null),
			"Bearer $expired" to TokenCheck(TokenValidationResult.INACTIVE, AZURE),
			"Bearer $valid" to TokenCheck(TokenValidationResult.OK, TOKENX),
		)

		cases.forEach { (authorization, expected) ->
			SecurityContextHolder.clearContext()
			assertEquals(expected, check(runFilter(authorization).first), "Authorization: $authorization")
		}
	}

	@Test
	fun `Texas down is recorded as unavailable with the issuer`() {
		every { introspector.introspect(any(), any()) } throws TokenServiceUnavailableException("Texas svarte 503 på introspeksjon")

		assertEquals(TokenCheck(TokenValidationResult.UNAVAILABLE, AZURE), check(runFilter("Bearer ${token(azureIssuer)}").first))
	}

	@Test
	fun `nothing is recorded when the request is already authenticated`() {
		SecurityContextHolder.getContext().authentication = TokenAuthentication(TOKENX, mapOf("acr" to "Level4"), "tokenx-token")

		assertNull(check(runFilter("Bearer ${token(tokenxIssuer)}").first))
	}
}
