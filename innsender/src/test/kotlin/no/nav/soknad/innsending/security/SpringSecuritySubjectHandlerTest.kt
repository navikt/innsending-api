package no.nav.soknad.innsending.security

import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.InvalidUserIdentityException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

// Samme tilfeller som SubjectHandlerImplTest, med autentiseringen fra Spring Security (#461, steg 3f)
class SpringSecuritySubjectHandlerTest {

	private val handler = SpringSecuritySubjectHandler()

	@AfterEach
	fun ryddOpp() = SecurityContextHolder.clearContext()

	private fun innlogget(utsteder: TokenUtsteder, vararg claims: Pair<String, Any?>, token: String = "token") {
		SecurityContextHolder.getContext().authentication = TokenAutentisering(utsteder, mapOf(*claims), token)
	}

	@Test
	fun `pid går foran sub for TokenX`() {
		innlogget(TokenUtsteder.TOKENX, "pid" to "authenticated-user", "sub" to "subject")
		assertEquals("authenticated-user", handler.getUserIdFromToken())
	}

	@Test
	fun `uten pid brukes sub for TokenX`() {
		innlogget(TokenUtsteder.TOKENX, "sub" to "authenticated-user")
		assertEquals("authenticated-user", handler.getUserIdFromToken())
	}

	@Test
	fun `pid med feil type avvises uten å bruke sub og uten claimverdier i feilen`() {
		listOf(listOf("private-claim-value"), 42, mapOf("value" to "private-claim-value")).forEach { pid ->
			innlogget(TokenUtsteder.TOKENX, "pid" to pid, "sub" to "subject")

			val feil = assertFailsWith<InvalidUserIdentityException> { handler.getUserIdFromToken() }
			assertEquals("Autentisering feilet", feil.message)
			assertNull(feil.cause)
		}
	}

	@Test
	fun `mangler både pid og sub`() {
		innlogget(TokenUtsteder.TOKENX)
		assertFailsWith<InvalidUserIdentityException> { handler.getUserIdFromToken() }
	}

	@Test
	fun `uten autentisering gir generell serverfeil`() {
		val feil = assertFailsWith<BackendErrorException> { handler.getUserIdFromToken() }
		assertEquals("Autentisering kunne ikke fullføres", feil.message)
		assertNull(feil.cause)
	}

	@Test
	fun `Azure-token gir ikke innbyggeridentitet`() {
		innlogget(TokenUtsteder.AZURE, "pid" to "12345678901", "sub" to "subject")
		assertFailsWith<BackendErrorException> { handler.getUserIdFromToken() }
	}

	@Test
	fun `annen type autentisering gir ikke identitet`() {
		SecurityContextHolder.getContext().authentication = TestingAuthenticationToken("12345678901", null)
		assertFailsWith<BackendErrorException> { handler.getUserIdFromToken() }
	}

	@Test
	fun `getToken gir bare TokenX-token`() {
		innlogget(TokenUtsteder.TOKENX, token = "tokenx-token")
		assertEquals("tokenx-token", handler.getToken())

		innlogget(TokenUtsteder.AZURE, token = "azure-token")
		assertFailsWith<BackendErrorException> { handler.getToken() }
	}

	@Test
	fun `klient-ID leses fra client_id og ellers azp`() {
		innlogget(TokenUtsteder.TOKENX, "client_id" to "dev-gcp:team:fyllut", "azp" to "annen")
		assertEquals("dev-gcp:team:fyllut", handler.getClientId())

		innlogget(TokenUtsteder.AZURE, "azp" to "azure-klient")
		assertEquals("azure-klient", handler.getClientId())
	}

	@Test
	fun `NAVident leses bare fra Azure-token`() {
		innlogget(TokenUtsteder.AZURE, "NAVident" to "Z123456")
		assertEquals("Z123456", handler.getNavIdent())

		innlogget(TokenUtsteder.TOKENX, "NAVident" to "Z123456")
		assertFailsWith<RuntimeException> { handler.getNavIdent() }
	}
}
