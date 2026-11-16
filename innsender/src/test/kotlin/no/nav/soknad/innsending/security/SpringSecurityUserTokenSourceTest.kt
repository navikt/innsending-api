package no.nav.soknad.innsending.security

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpringSecurityUserTokenSourceTest {

	private val source = SpringSecurityUserTokenSource()

	@AfterEach
	fun cleanUp() = SecurityContextHolder.clearContext()

	@Test
	fun `gives the TokenX token`() {
		SecurityContextHolder.getContext().authentication =
			TokenAuthentication(TokenIssuer.TOKENX, mapOf("acr" to "Level4"), "tokenx-token")

		assertEquals("tokenx-token", source.getUserToken())
	}

	@Test
	fun `never gives an Azure token`() {
		SecurityContextHolder.getContext().authentication =
			TokenAuthentication(TokenIssuer.AZURE, emptyMap(), "azure-token")

		assertNull(source.getUserToken())
	}

	@Test
	fun `gives null without authentication, for example in a job`() {
		assertNull(source.getUserToken())
	}
}
