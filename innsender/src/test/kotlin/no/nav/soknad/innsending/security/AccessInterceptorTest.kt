package no.nav.soknad.innsending.security

import no.nav.soknad.innsending.consumerapis.token.TokenServiceUnavailableException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.method.HandlerMethod
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccessInterceptorTest {

	private val recorded = mutableListOf<TokenValidationOutcome>()
	private val interceptor = AccessInterceptor({ recorded.add(it) })

	@RequiresAzure
	class AzureController {
		fun get() = "ok"
	}

	class PublicController {
		fun get() = "ok"
	}

	private fun handler(bean: Any) = HandlerMethod(bean, bean.javaClass.getMethod("get"))

	@AfterEach
	fun cleanUp() = SecurityContextHolder.clearContext()

	private fun anonymous() {
		SecurityContextHolder.getContext().authentication =
			AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))
	}

	@Test
	fun `the right access lets the request through`() {
		SecurityContextHolder.getContext().authentication = TokenAuthentication(TokenIssuer.AZURE, emptyMap(), "t")

		assertTrue(interceptor.preHandle(MockHttpServletRequest(), MockHttpServletResponse(), handler(AzureController())))
	}

	@Test
	fun `missing access is rejected before the body is read`() {
		anonymous()

		assertFailsWith<AuthorizationDeniedException> {
			interceptor.preHandle(MockHttpServletRequest(), MockHttpServletResponse(), handler(AzureController()))
		}
	}

	@Test
	fun `Texas down gives 503 instead of 403`() {
		anonymous()
		val request = MockHttpServletRequest().apply { setAttribute(TokenAuthenticationFilter.TOKEN_VALIDATION_UNAVAILABLE, true) }

		assertFailsWith<TokenServiceUnavailableException> {
			interceptor.preHandle(request, MockHttpServletResponse(), handler(AzureController()))
		}
	}

	@Test
	fun `an endpoint without a rule lets the request through, also when Texas is down`() {
		anonymous()
		val request = MockHttpServletRequest().apply { setAttribute(TokenAuthenticationFilter.TOKEN_VALIDATION_UNAVAILABLE, true) }

		assertTrue(interceptor.preHandle(request, MockHttpServletResponse(), handler(PublicController())))
	}

	private fun requestWith(check: TokenCheck?) =
		MockHttpServletRequest().apply { check?.let { setAttribute(TokenCheck.ATTRIBUTE, it) } }

	private fun outcomeOf(request: MockHttpServletRequest) =
		request.getAttribute(TokenValidationOutcome.ATTRIBUTE) as TokenValidationOutcome?

	@Test
	fun `a let-through request is recorded as ok with the verified client`() {
		SecurityContextHolder.getContext().authentication =
			TokenAuthentication(TokenIssuer.AZURE, mapOf("azp_name" to "prod-gcp:flex:sykepengesoknad-backend"), "t")
		val request = requestWith(TokenCheck(TokenValidationResult.OK, TokenIssuer.AZURE))

		interceptor.preHandle(request, MockHttpServletResponse(), handler(AzureController()))

		val expected = TokenValidationOutcome(TokenValidationResult.OK, "azure", "prod-gcp:flex:sykepengesoknad-backend")
		assertEquals(listOf(expected), recorded)
		assertEquals(expected, outcomeOf(request))
	}

	@Test
	fun `a valid token without the right access is recorded as forbidden with the client`() {
		SecurityContextHolder.getContext().authentication =
			TokenAuthentication(TokenIssuer.TOKENX, mapOf("client_id" to "prod-gcp:team-soknad:send-inn-frontend"), "t")

		assertFailsWith<AuthorizationDeniedException> {
			interceptor.preHandle(requestWith(null), MockHttpServletResponse(), handler(AzureController()))
		}

		assertEquals(
			listOf(TokenValidationOutcome(TokenValidationResult.FORBIDDEN, "tokenx", "prod-gcp:team-soknad:send-inn-frontend")),
			recorded,
		)
	}

	@Test
	fun `a rejected token never uses claims for the client`() {
		anonymous()
		val request = requestWith(TokenCheck(TokenValidationResult.INACTIVE, TokenIssuer.AZURE))

		assertFailsWith<AuthorizationDeniedException> {
			interceptor.preHandle(request, MockHttpServletResponse(), handler(AzureController()))
		}

		assertEquals(listOf(TokenValidationOutcome(TokenValidationResult.INACTIVE, "azure", "unknown")), recorded)
	}

	@Test
	fun `a request without a filter result is recorded as missing`() {
		anonymous()

		assertFailsWith<AuthorizationDeniedException> {
			interceptor.preHandle(requestWith(null), MockHttpServletResponse(), handler(AzureController()))
		}

		assertEquals(listOf(TokenValidationOutcome(TokenValidationResult.MISSING, "unknown", "unknown")), recorded)
	}

	@Test
	fun `Texas down is recorded as unavailable and still gives 503`() {
		anonymous()
		val request = requestWith(TokenCheck(TokenValidationResult.UNAVAILABLE, TokenIssuer.TOKENX))
			.apply { setAttribute(TokenAuthenticationFilter.TOKEN_VALIDATION_UNAVAILABLE, true) }

		assertFailsWith<TokenServiceUnavailableException> {
			interceptor.preHandle(request, MockHttpServletResponse(), handler(AzureController()))
		}

		assertEquals(listOf(TokenValidationOutcome(TokenValidationResult.UNAVAILABLE, "tokenx", "unknown")), recorded)
	}

	@Test
	fun `an endpoint without a rule is not recorded`() {
		anonymous()
		val request = requestWith(TokenCheck(TokenValidationResult.MISSING, null))

		interceptor.preHandle(request, MockHttpServletResponse(), handler(PublicController()))

		assertEquals(emptyList(), recorded)
		assertNull(outcomeOf(request))
	}

	@Test
	fun `an error in the recorder never changes the decision`() {
		val failing = AccessInterceptor({ throw IllegalStateException("teller feilet") })

		SecurityContextHolder.getContext().authentication = TokenAuthentication(TokenIssuer.AZURE, emptyMap(), "t")
		assertTrue(failing.preHandle(requestWith(null), MockHttpServletResponse(), handler(AzureController())))

		anonymous()
		assertFailsWith<AuthorizationDeniedException> {
			failing.preHandle(requestWith(null), MockHttpServletResponse(), handler(AzureController()))
		}

		val unavailable = requestWith(null).apply { setAttribute(TokenAuthenticationFilter.TOKEN_VALIDATION_UNAVAILABLE, true) }
		assertFailsWith<TokenServiceUnavailableException> {
			failing.preHandle(unavailable, MockHttpServletResponse(), handler(AzureController()))
		}
	}

	@Test
	fun `another authentication without access is recorded as forbidden without claims`() {
		SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken.authenticated("bruker", null, emptyList())

		assertFailsWith<AuthorizationDeniedException> {
			interceptor.preHandle(requestWith(null), MockHttpServletResponse(), handler(AzureController()))
		}

		assertEquals(listOf(TokenValidationOutcome(TokenValidationResult.FORBIDDEN, "unknown", "unknown")), recorded)
	}
}
