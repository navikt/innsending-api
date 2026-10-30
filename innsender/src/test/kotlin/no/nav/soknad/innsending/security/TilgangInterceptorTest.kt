package no.nav.soknad.innsending.security

import no.nav.soknad.innsending.consumerapis.texas.TexasUnavailableException
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

class TilgangInterceptorTest {

	private val interceptor = TilgangInterceptor()

	@KreverAzure
	class AzureController {
		fun hent() = "ok"
	}

	class OffentligController {
		fun hent() = "ok"
	}

	private fun handler(bean: Any) = HandlerMethod(bean, bean.javaClass.getMethod("hent"))

	@AfterEach
	fun ryddOpp() = SecurityContextHolder.clearContext()

	private fun anonym() {
		SecurityContextHolder.getContext().authentication =
			AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))
	}

	@Test
	fun `riktig tilgang slipper gjennom`() {
		SecurityContextHolder.getContext().authentication = TokenAutentisering(TokenUtsteder.AZURE, emptyMap(), "t")

		assertTrue(interceptor.preHandle(MockHttpServletRequest(), MockHttpServletResponse(), handler(AzureController())))
	}

	@Test
	fun `manglende tilgang avvises før bodyen leses`() {
		anonym()

		assertFailsWith<AuthorizationDeniedException> {
			interceptor.preHandle(MockHttpServletRequest(), MockHttpServletResponse(), handler(AzureController()))
		}
	}

	@Test
	fun `Texas nede gir 503 i stedet for 403`() {
		anonym()
		val request = MockHttpServletRequest().apply { setAttribute(TexasAutentiseringFilter.TEXAS_FEIL, true) }

		assertFailsWith<TexasUnavailableException> {
			interceptor.preHandle(request, MockHttpServletResponse(), handler(AzureController()))
		}
	}

	@Test
	fun `endepunkt uten regel slipper gjennom, også når Texas er nede`() {
		anonym()
		val request = MockHttpServletRequest().apply { setAttribute(TexasAutentiseringFilter.TEXAS_FEIL, true) }

		assertTrue(interceptor.preHandle(request, MockHttpServletResponse(), handler(OffentligController())))
	}
}
