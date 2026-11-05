package no.nav.soknad.innsending.security

import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import io.mockk.every
import io.mockk.mockk
import no.nav.security.token.support.core.context.TokenValidationContext
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.security.token.support.core.jwt.JwtToken
import no.nav.soknad.innsending.util.Constants.AZURE
import no.nav.soknad.innsending.util.Constants.TOKENX
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class TokenSupportBridgeFilterTest {

	@AfterEach
	fun cleanUp() = SecurityContextHolder.clearContext()

	private fun jwt(vararg claims: Pair<String, Any>): JwtToken {
		val claimSet = JWTClaimsSet.Builder().apply { claims.forEach { (name, value) -> claim(name, value) } }.build()
		return JwtToken(PlainJWT(claimSet).serialize())
	}

	private fun runFilter(tokens: Map<String, JwtToken>): org.springframework.security.core.Authentication? {
		val holder = mockk<TokenValidationContextHolder>()
		every { holder.getTokenValidationContext() } returns TokenValidationContext(tokens)
		var captured: org.springframework.security.core.Authentication? = null
		val chain = MockFilterChain(object : jakarta.servlet.http.HttpServlet() {
			override fun service(req: jakarta.servlet.ServletRequest, res: jakarta.servlet.ServletResponse) {
				captured = SecurityContextHolder.getContext().authentication
			}
		})
		TokenSupportBridgeFilter(holder).doFilter(MockHttpServletRequest(), MockHttpServletResponse(), chain)
		return captured
	}

	@Test
	fun `a validated TokenX token becomes a citizen with token and claims`() {
		val token = jwt("pid" to "12345678901", "acr" to "idporten-loa-high")

		val authentication = runFilter(mapOf(TOKENX to token)) as TokenAuthentication

		assertEquals(setOf(TokenIssuer.TOKENX), authentication.tokens.keys)
		assertEquals(token.encodedToken, authentication.credentials)
		assertEquals("12345678901", authentication.token(TokenIssuer.TOKENX)!!.claims["pid"])
		assertEquals(setOf(Access.TOKENX, Access.CITIZEN), authentication.authorities.map { it.authority }.toSet())
	}

	@Test
	fun `a validated Azure token becomes Azure with roles`() {
		val authentication = runFilter(mapOf(AZURE to jwt("roles" to listOf("nologin-access")))) as TokenAuthentication

		assertEquals(setOf(TokenIssuer.AZURE), authentication.tokens.keys)
		assertEquals(setOf(Access.AZURE, Access.AZURE_NOLOGIN), authentication.authorities.map { it.authority }.toSet())
	}

	// Token-support godtar flere kommaseparerte token. Tilgangene fra begge gjelder, som før broen
	@Test
	fun `a TokenX and an Azure token in one request give authorities from both`() {
		val authentication = runFilter(
			mapOf(TOKENX to jwt("pid" to "12345678901", "acr" to "Level4"), AZURE to jwt("scp" to "admin-access defaultaccess"))
		) as TokenAuthentication

		assertEquals(
			setOf(Access.TOKENX, Access.CITIZEN, Access.AZURE, Access.AZURE_ADMIN),
			authentication.authorities.map { it.authority }.toSet()
		)
	}

	@Test
	fun `without a validated token no authentication is set`() {
		assertNull(runFilter(emptyMap()))
	}

	@Test
	fun `existing authentication is not overwritten`() {
		val existing = TestingAuthenticationToken("annen", null)
		SecurityContextHolder.getContext().authentication = existing

		assertSame(existing, runFilter(mapOf(TOKENX to jwt("acr" to "Level4"))))
	}

	@Test
	fun `toString shows neither token nor claims`() {
		val token = jwt("pid" to "12345678901", "acr" to "Level4")
		val text = TokenAuthentication(TokenIssuer.TOKENX, token.jwtTokenClaims.allClaims, token.encodedToken).toString()

		assertFalse(text.contains("12345678901"))
		assertFalse(text.contains(token.encodedToken))
	}
}
