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

class BrukerTokenKildeTest {

	private val tokenxToken = mockk<JwtToken> { every { encodedToken } returns "tokenx-token" }
	private val azureToken = mockk<JwtToken> { every { encodedToken } returns "azure-token" }

	@Test
	fun `gir TokenX-tokenet`() {
		val kilde = kildeMed(mapOf(TOKENX to tokenxToken))

		assertEquals("tokenx-token", kilde.hentTokenXToken())
	}

	@Test
	fun `gir aldri et Azure-token`() {
		val kilde = kildeMed(mapOf(AZURE to azureToken))

		assertNull(kilde.hentTokenXToken())
	}

	@Test
	fun `velger TokenX selv om Azure-tokenet kommer først`() {
		val kilde = kildeMed(linkedMapOf(AZURE to azureToken, TOKENX to tokenxToken))

		assertEquals("tokenx-token", kilde.hentTokenXToken())
	}

	@Test
	fun `krevTokenXToken kaster uten token og uten å nevne token i meldingen`() {
		val kilde = kildeMed(mapOf(AZURE to azureToken))

		val feil = assertFailsWith<ManglerBrukerTokenException> { kilde.krevTokenXToken("dev-fss:pdl:pdl-api") }
		assertFalse(feil.message!!.contains("azure-token"))
	}

	private fun kildeMed(tokens: Map<String, JwtToken>): BrukerTokenKilde {
		val holder = mockk<TokenValidationContextHolder>()
		every { holder.getTokenValidationContext() } returns TokenValidationContext(tokens)
		return TokenSupportBrukerTokenKilde(holder)
	}
}
