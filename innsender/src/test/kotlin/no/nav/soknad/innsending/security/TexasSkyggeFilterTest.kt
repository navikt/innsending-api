package no.nav.soknad.innsending.security

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertNotNull

class TexasSkyggeFilterTest {

	private val skygge = mockk<TexasSkygge> { every { sammenlign(any(), any()) } just runs }
	private val filter = TexasSkyggeFilter(skygge)

	@AfterEach
	fun ryddOpp() = SecurityContextHolder.clearContext()

	private fun kjør(authorization: String?): MockFilterChain {
		val request = MockHttpServletRequest().apply { authorization?.let { addHeader("Authorization", it) } }
		return MockFilterChain().also { filter.doFilter(request, MockHttpServletResponse(), it) }
	}

	@Test
	fun `sender bearer-token og token-supports avgjørelse`() {
		val autentisering = TokenAutentisering(TokenUtsteder.TOKENX, mapOf("acr" to "Level4"), "tokenet")
		SecurityContextHolder.getContext().authentication = autentisering

		val chain = kjør("Bearer tokenet")

		verify { skygge.sammenlign("tokenet", autentisering) }
		assertNotNull(chain.request, "Forespørselen skal alltid gå videre")
	}

	@Test
	fun `avvist token sendes uten autentisering`() {
		kjør("bearer ugyldig")

		verify { skygge.sammenlign("ugyldig", null) }
	}

	@Test
	fun `uten bearer-token sammenlignes ingenting`() {
		listOf(null, "", "Basic abc", "Bearer ", "Bearer").forEach { kjør(it) }

		verify(exactly = 0) { skygge.sammenlign(any(), any()) }
	}
}
