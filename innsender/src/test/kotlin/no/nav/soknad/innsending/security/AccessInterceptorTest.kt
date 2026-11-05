package no.nav.soknad.innsending.security

import no.nav.soknad.innsending.consumerapis.token.TokenServiceUnavailableException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.method.HandlerMethod
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AccessInterceptorTest {

	private val interceptor = AccessInterceptor()

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
}
