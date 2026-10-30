package no.nav.soknad.innsending.security

import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.soknad.innsending.consumerapis.texas.TexasIntrospeksjon
import no.nav.soknad.innsending.consumerapis.texas.TexasIntrospeksjonKlient.Companion.ENTRA_ID
import no.nav.soknad.innsending.consumerapis.texas.TexasIntrospeksjonKlient.Companion.TOKENX
import no.nav.soknad.innsending.consumerapis.texas.TexasUnavailableException
import no.nav.soknad.innsending.consumerapis.texas.TokenIntrospeksjon
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

class TexasAutentiseringFilterTest {

	private val tokenxIssuer = "https://tokenx.test"
	private val azureIssuer = "https://login.test/tenant/v2.0"
	private val issuere = mapOf(TokenUtsteder.TOKENX to tokenxIssuer, TokenUtsteder.AZURE to azureIssuer)

	private val introspeksjon = mockk<TokenIntrospeksjon>()

	@AfterEach
	fun ryddOpp() = SecurityContextHolder.clearContext()

	// Usignert, fordi filteret bare leser iss for å velge utsteder. Introspeksjonen er mocket
	private fun token(issuer: String?) = PlainJWT(JWTClaimsSet.Builder().issuer(issuer).build()).serialize()

	private fun kjør(authorization: String?): Pair<MockHttpServletRequest, Authentication?> {
		val request = MockHttpServletRequest().apply { authorization?.let { addHeader("Authorization", it) } }
		var underveis: Authentication? = null
		TexasAutentiseringFilter(introspeksjon, issuere)
			.doFilter(request, MockHttpServletResponse()) { _, _ -> underveis = SecurityContextHolder.getContext().authentication }
		return request to underveis
	}

	@Test
	fun `Azure-token valideres bare som Azure, med ett kall`() {
		val azure = token(azureIssuer)
		every { introspeksjon.introspekter(ENTRA_ID, azure) } returns
			TexasIntrospeksjon(true, mapOf("azp" to "klient", "roles" to listOf("nologin-access")))

		val autentisering = kjør("Bearer $azure").second as TokenAutentisering

		assertEquals(setOf(TokenUtsteder.AZURE), autentisering.tokens.keys)
		assertEquals(setOf(Tilgang.AZURE, Tilgang.AZURE_NOLOGIN), autentisering.authorities.map { it.authority }.toSet())
		assertEquals(azure, autentisering.credentials)
		verify(exactly = 1) { introspeksjon.introspekter(any(), any()) }
	}

	@Test
	fun `TokenX-token valideres bare som TokenX, med ett kall`() {
		val tokenx = token(tokenxIssuer)
		every { introspeksjon.introspekter(TOKENX, tokenx) } returns TexasIntrospeksjon(true, mapOf("pid" to "12345678901", "acr" to "Level4"))

		val autentisering = kjør("Bearer $tokenx").second as TokenAutentisering

		assertEquals(setOf(TokenUtsteder.TOKENX), autentisering.tokens.keys)
		verify(exactly = 1) { introspeksjon.introspekter(any(), any()) }
	}

	@Test
	fun `Texas nede for Azure hindrer ikke TokenX`() {
		val tokenx = token(tokenxIssuer)
		every { introspeksjon.introspekter(ENTRA_ID, any()) } throws TexasUnavailableException("Texas svarte 503 på introspeksjon")
		every { introspeksjon.introspekter(TOKENX, tokenx) } returns TexasIntrospeksjon(true, mapOf("acr" to "Level4"))

		val (request, autentisering) = kjør("Bearer $tokenx")

		assertEquals(setOf(TokenUtsteder.TOKENX), (autentisering as TokenAutentisering).tokens.keys)
		assertNull(request.getAttribute(TexasAutentiseringFilter.TEXAS_FEIL))
	}

	@Test
	fun `ukjent, manglende eller uleselig iss gir ingen autentisering og ingen kall`() {
		listOf(token("https://ukjent.test"), token(null), "ikke-en-jwt").forEach { assertNull(kjør("Bearer $it").second) }

		verify(exactly = 0) { introspeksjon.introspekter(any(), any()) }
	}

	@Test
	fun `ugyldig token gir ingen autentisering`() {
		every { introspeksjon.introspekter(ENTRA_ID, any()) } returns TexasIntrospeksjon(false, emptyMap())

		assertNull(kjør("Bearer ${token(azureIssuer)}").second)
	}

	@Test
	fun `Texas nede merker forespørselen og slipper den videre`() {
		every { introspeksjon.introspekter(any(), any()) } throws TexasUnavailableException("Texas svarte 503 på introspeksjon")

		val (request, autentisering) = kjør("Bearer ${token(azureIssuer)}")

		assertNull(autentisering)
		assertEquals(true, request.getAttribute(TexasAutentiseringFilter.TEXAS_FEIL))
	}

	@Test
	fun `eksisterende autentisering overskrives ikke og sendes ikke til Texas`() {
		val eksisterende = TokenAutentisering(TokenUtsteder.TOKENX, mapOf("acr" to "Level4"), "tokenx-token")
		SecurityContextHolder.getContext().authentication = eksisterende

		assertSame(eksisterende, kjør("Bearer ${token(tokenxIssuer)}").second)
		verify(exactly = 0) { introspeksjon.introspekter(any(), any()) }
	}

	@Test
	fun `uten token eller med flere token spørres ikke Texas`() {
		listOf(null, "Basic abc", "Bearer a,b").forEach { assertNull(kjør(it).second) }

		verify(exactly = 0) { introspeksjon.introspekter(any(), any()) }
	}

	@Test
	fun `utsteder uten konfigurert issuer stopper oppstarten`() {
		assertFailsWith<IllegalArgumentException> {
			TexasAutentiseringFilter(introspeksjon, mapOf(TokenUtsteder.AZURE to azureIssuer))
		}
	}
}
