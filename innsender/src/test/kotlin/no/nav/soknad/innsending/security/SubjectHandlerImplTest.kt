package no.nav.soknad.innsending.security

import com.nimbusds.jwt.JWTClaimsSet
import io.mockk.every
import io.mockk.mockk
import no.nav.security.token.support.core.context.TokenValidationContext
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.security.token.support.core.jwt.JwtTokenClaims
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.InvalidUserIdentityException
import no.nav.soknad.innsending.util.Constants.TOKENX
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class SubjectHandlerImplTest {

	private val context = mockk<TokenValidationContext>()
	private val holder = mockk<TokenValidationContextHolder>()
	private val handler = SubjectHandlerImpl(holder)

	private fun claims(pid: Any?, sub: String?) {
		every { holder.getTokenValidationContext() } returns context
		every { context.hasTokenFor(TOKENX) } returns true
		every { context.getClaims(TOKENX) } returns JwtTokenClaims(
			JWTClaimsSet.Builder().claim("pid", pid).subject(sub).build()
		)
	}

	@Test
	fun `pid takes precedence over sub for TokenX`() {
		claims("authenticated-user", "subject")
		assertEquals("authenticated-user", handler.getUserIdFromToken())
	}

	@Test
	fun `absent pid still uses sub for TokenX`() {
		claims(null, "authenticated-user")
		assertEquals("authenticated-user", handler.getUserIdFromToken())
	}

	@Test
	fun `malformed pid is rejected without using sub or including claim values`() {
		listOf(listOf("private-claim-value"), 42, mapOf("value" to "private-claim-value")).forEach { pid ->
			claims(pid, "subject")

			val exception = assertFailsWith<InvalidUserIdentityException> { handler.getUserIdFromToken() }
			assertEquals("Autentisering feilet", exception.message)
			assertNull(exception.cause)
		}
	}

	@Test
	fun `missing user claims are rejected`() {
		claims(null, null)

		assertFailsWith<InvalidUserIdentityException> { handler.getUserIdFromToken() }
	}

	@Test
	fun `missing request context is not translated into a user claim error`() {
		val failure = IllegalStateException("Missing request context")
		every { holder.getTokenValidationContext() } throws failure

		assertSame(failure, assertFailsWith<IllegalStateException> { handler.getUserIdFromToken() })
	}

	@Test
	fun `empty token context produces a generic server error instead of a library client error`() {
		every { holder.getTokenValidationContext() } returns TokenValidationContext(emptyMap())

		val exception = assertFailsWith<BackendErrorException> { handler.getUserIdFromToken() }
		assertEquals("Autentisering kunne ikke fullføres", exception.message)
		assertNull(exception.cause)
	}

	@Test
	fun `missing expected user issuer is not translated into a user claim error`() {
		claims(null, null)
		val failure = IllegalStateException("Missing user issuer")
		every { context.getClaims(TOKENX) } throws failure

		assertSame(failure, assertFailsWith<IllegalStateException> { handler.getUserIdFromToken() })
	}
}
