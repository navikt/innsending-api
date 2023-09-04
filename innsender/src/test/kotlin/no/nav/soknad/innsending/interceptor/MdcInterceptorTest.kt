package no.nav.soknad.innsending.interceptor

import no.nav.soknad.innsending.util.Constants.MDC_INNSENDINGS_ID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.HandlerMapping
import kotlin.test.assertNull

class MdcInterceptorTest {

	private lateinit var request: MockHttpServletRequest
	private lateinit var response: MockHttpServletResponse

	private lateinit var interceptor: MdcInterceptor

	@BeforeEach
	fun setUp() {
		MDC.clear()
		interceptor = MdcInterceptor()

		request = MockHttpServletRequest()
		response = MockHttpServletResponse()
	}

	@Test
	fun `Skal sette MDC fra header`() {
		// Gitt
		val headerName = "x-innsendingsid"
		val headerValue = "header123456"

		request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, emptyMap<String, String>())
		request.addHeader(headerName, headerValue)

		// Når
		interceptor.preHandle(request, response, Any())

		// Så
		assert(MDC.get(MDC_INNSENDINGS_ID) == headerValue)
	}

	@Test
	fun `Skal sette MDC fra pathVariable`() {
		// Gitt
		val pathVarName = "innsendingsid"
		val pathVarValue = "path123456"

		request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, mapOf(pathVarName to pathVarValue))

		// Når
		interceptor.preHandle(request, response, Any())

		// Så
		assert(MDC.get(MDC_INNSENDINGS_ID) == pathVarValue)
	}

	@Test
	fun `Skal resette MDC etter request er ferdig`() {
		// Når
		interceptor.afterCompletion(request, response, Any(), null)

		// Så
		assertNull(MDC.get(MDC_INNSENDINGS_ID))
	}
}
