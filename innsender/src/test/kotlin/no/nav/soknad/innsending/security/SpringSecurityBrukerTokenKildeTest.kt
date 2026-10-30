package no.nav.soknad.innsending.security

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpringSecurityBrukerTokenKildeTest {

	private val kilde = SpringSecurityBrukerTokenKilde()

	@AfterEach
	fun ryddOpp() = SecurityContextHolder.clearContext()

	@Test
	fun `gir TokenX-tokenet`() {
		SecurityContextHolder.getContext().authentication =
			TokenAutentisering(TokenUtsteder.TOKENX, mapOf("acr" to "Level4"), "tokenx-token")

		assertEquals("tokenx-token", kilde.hentTokenXToken())
	}

	@Test
	fun `gir aldri et Azure-token`() {
		SecurityContextHolder.getContext().authentication =
			TokenAutentisering(TokenUtsteder.AZURE, emptyMap(), "azure-token")

		assertNull(kilde.hentTokenXToken())
	}

	@Test
	fun `gir null uten autentisering, for eksempel i en jobb`() {
		assertNull(kilde.hentTokenXToken())
	}
}
