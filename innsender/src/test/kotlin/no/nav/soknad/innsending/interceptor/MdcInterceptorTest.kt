package no.nav.soknad.innsending.interceptor

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import no.nav.soknad.innsending.util.Constants.MDC_INNSENDINGS_ID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.web.servlet.HandlerMapping
import java.util.*
import kotlin.test.assertNull

class MdcInterceptorTest {

	private val request: HttpServletRequest = mockk(relaxed = true)
	private val response: HttpServletResponse = mockk(relaxed = true)

	private lateinit var interceptor: MdcInterceptor

	@BeforeEach
	fun setUp() {
		MDC.clear()
		interceptor = MdcInterceptor()
	}

	@AfterEach
	fun tearDown() {
		clearAllMocks()
	}

	@Test
	fun `Skal sette MDC fra header`() {
		// Gitt
		val headerName = "x-innsendingsid"
		val headerValue = "123456"

		every { request.headerNames } returns Collections.enumeration(listOf(headerName))
		every { request.getHeader(headerName) } returns headerValue
		every { request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) } returns emptyMap<String, String>()

		// Når
		interceptor.preHandle(request, response, Any())

		// Så
		assert(MDC.get(MDC_INNSENDINGS_ID) == headerValue)
	}

	@Test
	fun `Skal sette MDC fra pathVariable`() {
		// Gitt
		val pathVarName = "innsendingsid"
		val pathVarValue = "123456"

		every { request.getAttribute(any<String>()) } returns mapOf(pathVarName to pathVarValue)

		// Når
		interceptor.preHandle(request, response, Any())

		// Sår
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
