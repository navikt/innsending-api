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
	fun cleanUp() = SecurityContextHolder.clearContext()

	private fun authenticatedAs(issuer: TokenIssuer, vararg claims: Pair<String, Any?>, token: String = "token") {
		SecurityContextHolder.getContext().authentication = TokenAuthentication(issuer, mapOf(*claims), token)
	}

	@Test
	fun `pid takes precedence over sub for TokenX`() {
		authenticatedAs(TokenIssuer.TOKENX, "pid" to "authenticated-user", "sub" to "subject")
		assertEquals("authenticated-user", handler.getUserIdFromToken())
	}

	@Test
	fun `without pid sub is used for TokenX`() {
		authenticatedAs(TokenIssuer.TOKENX, "sub" to "authenticated-user")
		assertEquals("authenticated-user", handler.getUserIdFromToken())
	}

	@Test
	fun `a pid with the wrong type is rejected without using sub and without claim values in the error`() {
		listOf(listOf("private-claim-value"), 42, mapOf("value" to "private-claim-value")).forEach { pid ->
			authenticatedAs(TokenIssuer.TOKENX, "pid" to pid, "sub" to "subject")

			val error = assertFailsWith<InvalidUserIdentityException> { handler.getUserIdFromToken() }
			assertEquals("Autentisering feilet", error.message)
			assertNull(error.cause)
		}
	}

	@Test
	fun `missing both pid and sub`() {
		authenticatedAs(TokenIssuer.TOKENX)
		assertFailsWith<InvalidUserIdentityException> { handler.getUserIdFromToken() }
	}

	@Test
	fun `no authentication gives a generic server error`() {
		val error = assertFailsWith<BackendErrorException> { handler.getUserIdFromToken() }
		assertEquals("Autentisering kunne ikke fullføres", error.message)
		assertNull(error.cause)
	}

	@Test
	fun `an Azure token gives no citizen identity`() {
		authenticatedAs(TokenIssuer.AZURE, "pid" to "12345678901", "sub" to "subject")
		assertFailsWith<BackendErrorException> { handler.getUserIdFromToken() }
	}

	@Test
	fun `another authentication type gives no identity`() {
		SecurityContextHolder.getContext().authentication = TestingAuthenticationToken("12345678901", null)
		assertFailsWith<BackendErrorException> { handler.getUserIdFromToken() }
	}

	@Test
	fun `getToken gives only a TokenX token`() {
		authenticatedAs(TokenIssuer.TOKENX, token = "tokenx-token")
		assertEquals("tokenx-token", handler.getToken())

		authenticatedAs(TokenIssuer.AZURE, token = "azure-token")
		assertFailsWith<BackendErrorException> { handler.getToken() }
	}

	@Test
	fun `the client ID is read from client_id and otherwise azp`() {
		authenticatedAs(TokenIssuer.TOKENX, "client_id" to "dev-gcp:team:fyllut", "azp" to "annen")
		assertEquals("dev-gcp:team:fyllut", handler.getClientId())

		authenticatedAs(TokenIssuer.AZURE, "azp" to "azure-klient")
		assertEquals("azure-klient", handler.getClientId())
	}

	@Test
	fun `with both a TokenX and an Azure token each identity is read from its own token`() {
		SecurityContextHolder.getContext().authentication = TokenAuthentication(
			mapOf(
				TokenIssuer.AZURE to ValidatedToken(mapOf("NAVident" to "Z123456", "azp" to "azure-klient"), "azure-token"),
				TokenIssuer.TOKENX to ValidatedToken(mapOf("pid" to "12345678901", "client_id" to "tokenx-klient"), "tokenx-token"),
			)
		)

		assertEquals("12345678901", handler.getUserIdFromToken())
		assertEquals("tokenx-token", handler.getToken())
		assertEquals("tokenx-klient", handler.getClientId())
		assertEquals("Z123456", handler.getNavIdent())
	}

	@Test
	fun `NAVident is read only from an Azure token`() {
		authenticatedAs(TokenIssuer.AZURE, "NAVident" to "Z123456")
		assertEquals("Z123456", handler.getNavIdent())

		authenticatedAs(TokenIssuer.TOKENX, "NAVident" to "Z123456")
		assertFailsWith<RuntimeException> { handler.getNavIdent() }
	}
}
