package no.nav.soknad.innsending.security

import io.mockk.every
import io.mockk.mockk
import no.nav.security.token.support.core.context.TokenValidationContext
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.security.token.support.core.jwt.JwtToken
import no.nav.soknad.innsending.util.Constants.AZURE
import no.nav.soknad.innsending.util.Constants.TOKENX
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class TokenSupportUserTokenSourceTest {

	private val tokenxToken = mockk<JwtToken> { every { encodedToken } returns "tokenx-token" }
	private val azureToken = mockk<JwtToken> { every { encodedToken } returns "azure-token" }

	@Test
	fun `gives the TokenX token`() {
		val source = sourceWith(mapOf(TOKENX to tokenxToken))

		assertEquals("tokenx-token", source.getUserToken())
	}

	@Test
	fun `never gives an Azure token`() {
		val source = sourceWith(mapOf(AZURE to azureToken))

		assertNull(source.getUserToken())
	}

	@Test
	fun `chooses TokenX even when the Azure token comes first`() {
		val source = sourceWith(linkedMapOf(AZURE to azureToken, TOKENX to tokenxToken))

		assertEquals("tokenx-token", source.getUserToken())
	}

	@Test
	fun `requireUserToken throws without a token and without mentioning the token in the message`() {
		val source = sourceWith(mapOf(AZURE to azureToken))

		val error = assertFailsWith<MissingUserTokenException> { source.requireUserToken("dev-fss:pdl:pdl-api") }
		assertFalse(error.message!!.contains("azure-token"))
	}

	@Test
	fun `gives null outside an HTTP request`() {
		val holder = mockk<TokenValidationContextHolder>()
		every { holder.getTokenValidationContext() } throws IllegalStateException("No thread-bound request found")

		assertNull(TokenSupportUserTokenSource(holder).getUserToken())
	}

	private fun sourceWith(tokens: Map<String, JwtToken>): UserTokenSource {
		val holder = mockk<TokenValidationContextHolder>()
		every { holder.getTokenValidationContext() } returns TokenValidationContext(tokens)
		return TokenSupportUserTokenSource(holder)
	}
}
