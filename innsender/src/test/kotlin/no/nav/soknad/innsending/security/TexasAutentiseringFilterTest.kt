package no.nav.soknad.innsending.security

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.soknad.innsending.consumerapis.texas.TexasIntrospeksjon
import no.nav.soknad.innsending.consumerapis.texas.TexasIntrospeksjonKlient.Companion.ENTRA_ID
import no.nav.soknad.innsending.consumerapis.texas.TexasUnavailableException
import no.nav.soknad.innsending.consumerapis.texas.TokenIntrospeksjon
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class TexasAutentiseringFilterTest {

	private val introspeksjon = mockk<TokenIntrospeksjon>()
	private val filter = TexasAutentiseringFilter(introspeksjon, setOf(TokenUtsteder.AZURE))

	@AfterEach
	fun ryddOpp() = SecurityContextHolder.clearContext()

	private fun kjør(authorization: String?): Pair<MockHttpServletRequest, Authentication?> {
		val request = MockHttpServletRequest().apply { authorization?.let { addHeader("Authorization", it) } }
		var underveis: Authentication? = null
		filter.doFilter(request, MockHttpServletResponse()) { _, _ -> underveis = SecurityContextHolder.getContext().authentication }
		return request to underveis
	}

	@Test
	fun `gyldig Azure-token blir autentisering med claimene fra Texas`() {
		every { introspeksjon.introspekter(ENTRA_ID, "azure-token") } returns
			TexasIntrospeksjon(true, mapOf("azp" to "klient", "roles" to listOf("nologin-access")))

		val autentisering = kjør("Bearer azure-token").second as TokenAutentisering

		assertEquals(setOf(TokenUtsteder.AZURE), autentisering.tokens.keys)
		assertEquals(setOf(Tilgang.AZURE, Tilgang.AZURE_NOLOGIN), autentisering.authorities.map { it.authority }.toSet())
		assertEquals("azure-token", autentisering.credentials)
	}

	@Test
	fun `ugyldig token gir ingen autentisering`() {
		every { introspeksjon.introspekter(ENTRA_ID, any()) } returns TexasIntrospeksjon(false, emptyMap())

		assertNull(kjør("Bearer ugyldig").second)
	}

	@Test
	fun `Texas nede merker forespørselen og slipper den videre`() {
		every { introspeksjon.introspekter(any(), any()) } throws TexasUnavailableException("Texas svarte 503 på introspeksjon")

		val (request, autentisering) = kjør("Bearer azure-token")

		assertNull(autentisering)
		assertEquals(true, request.getAttribute(TexasAutentiseringFilter.TEXAS_FEIL))
	}

	@Test
	fun `token som broen allerede har autentisert, sendes ikke til Texas`() {
		val fraBroen = TokenAutentisering(TokenUtsteder.TOKENX, mapOf("acr" to "Level4"), "tokenx-token")
		SecurityContextHolder.getContext().authentication = fraBroen

		assertSame(fraBroen, kjør("Bearer tokenx-token").second)
		verify(exactly = 0) { introspeksjon.introspekter(any(), any()) }
	}

	@Test
	fun `uten token eller med flere token spørres ikke Texas`() {
		listOf(null, "Basic abc", "Bearer a,b").forEach { assertNull(kjør(it).second) }

		verify(exactly = 0) { introspeksjon.introspekter(any(), any()) }
	}
}
