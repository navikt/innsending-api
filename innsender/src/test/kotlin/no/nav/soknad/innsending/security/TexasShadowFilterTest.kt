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

class TexasShadowFilterTest {

	private val shadow = mockk<TexasShadow> {
		every { compare(any(), any()) } just runs
		every { multipleTokens() } just runs
	}
	private val filter = TexasShadowFilter(shadow)

	@AfterEach
	fun cleanUp() = SecurityContextHolder.clearContext()

	private fun runFilter(authorization: String?): MockFilterChain {
		val request = MockHttpServletRequest().apply { authorization?.let { addHeader("Authorization", it) } }
		return MockFilterChain().also { filter.doFilter(request, MockHttpServletResponse(), it) }
	}

	@Test
	fun `sends the bearer token and the token-support decision`() {
		val authentication = TokenAuthentication(TokenIssuer.TOKENX, mapOf("acr" to "Level4"), "tokenet")
		SecurityContextHolder.getContext().authentication = authentication

		val chain = runFilter("Bearer tokenet")

		verify { shadow.compare("tokenet", authentication) }
		assertNotNull(chain.request, "Forespørselen skal alltid gå videre")
	}

	@Test
	fun `a rejected token is sent without authentication`() {
		runFilter("bearer ugyldig")

		verify { shadow.compare("ugyldig", null) }
	}

	@Test
	fun `several comma separated tokens are not sent to Texas`() {
		runFilter("Bearer tokenx-token,azure-token")

		verify(exactly = 0) { shadow.compare(any(), any()) }
		verify { shadow.multipleTokens() }
	}

	@Test
	fun `without a bearer token nothing is compared`() {
		listOf(null, "", "Basic abc", "Bearer ", "Bearer").forEach { runFilter(it) }

		verify(exactly = 0) { shadow.compare(any(), any()) }
	}
}
