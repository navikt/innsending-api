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

class TokenSupportBroFilterTest {

	@AfterEach
	fun ryddOpp() = SecurityContextHolder.clearContext()

	private fun jwt(vararg claims: Pair<String, Any>): JwtToken {
		val claimSet = JWTClaimsSet.Builder().apply { claims.forEach { (navn, verdi) -> claim(navn, verdi) } }.build()
		return JwtToken(PlainJWT(claimSet).serialize())
	}

	private fun kjør(tokens: Map<String, JwtToken>): org.springframework.security.core.Authentication? {
		val holder = mockk<TokenValidationContextHolder>()
		every { holder.getTokenValidationContext() } returns TokenValidationContext(tokens)
		var underveis: org.springframework.security.core.Authentication? = null
		val chain = MockFilterChain(object : jakarta.servlet.http.HttpServlet() {
			override fun service(req: jakarta.servlet.ServletRequest, res: jakarta.servlet.ServletResponse) {
				underveis = SecurityContextHolder.getContext().authentication
			}
		})
		TokenSupportBroFilter(holder).doFilter(MockHttpServletRequest(), MockHttpServletResponse(), chain)
		return underveis
	}

	@Test
	fun `validert TokenX-token blir innbygger med token og claims`() {
		val token = jwt("pid" to "12345678901", "acr" to "idporten-loa-high")

		val autentisering = kjør(mapOf(TOKENX to token)) as TokenAutentisering

		assertEquals(TokenUtsteder.TOKENX, autentisering.utsteder)
		assertEquals(token.encodedToken, autentisering.credentials)
		assertEquals("12345678901", autentisering.claims["pid"])
		assertEquals(setOf(Tilgang.TOKENX, Tilgang.INNBYGGER), autentisering.authorities.map { it.authority }.toSet())
	}

	@Test
	fun `validert Azure-token blir Azure med roller`() {
		val autentisering = kjør(mapOf(AZURE to jwt("roles" to listOf("nologin-access")))) as TokenAutentisering

		assertEquals(TokenUtsteder.AZURE, autentisering.utsteder)
		assertEquals(setOf(Tilgang.AZURE, Tilgang.AZURE_NOLOGIN), autentisering.authorities.map { it.authority }.toSet())
	}

	@Test
	fun `uten validert token settes ingen autentisering`() {
		assertNull(kjør(emptyMap()))
	}

	@Test
	fun `eksisterende autentisering overskrives ikke`() {
		val eksisterende = TestingAuthenticationToken("annen", null)
		SecurityContextHolder.getContext().authentication = eksisterende

		assertSame(eksisterende, kjør(mapOf(TOKENX to jwt("acr" to "Level4"))))
	}

	@Test
	fun `toString viser verken token eller claims`() {
		val token = jwt("pid" to "12345678901", "acr" to "Level4")
		val tekst = TokenAutentisering(TokenUtsteder.TOKENX, token.jwtTokenClaims.allClaims, token.encodedToken).toString()

		assertFalse(tekst.contains("12345678901"))
		assertFalse(tekst.contains(token.encodedToken))
	}
}
