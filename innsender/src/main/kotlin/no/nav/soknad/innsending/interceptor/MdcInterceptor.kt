package no.nav.soknad.innsending.interceptor

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import no.nav.soknad.innsending.util.Constants.MDC_INNSENDINGS_ID
import no.nav.soknad.innsending.util.MDCUtil
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping
import java.util.*

@Component
class MdcInterceptor : HandlerInterceptor {
	// Legg til innsendingsId i MDC fra header eller pathvariabel
	override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
		val pathVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as Map<*, *>
		val headerNames = Collections.list(request.headerNames)

		val variations = arrayOf("x-innsendingid", "x-innsendingsid", "innsendingid", "innsendingsid")
		val innsendingsIdHeader = variations
			.map { variation -> headerNames.find { it.equals(variation, ignoreCase = true) } }
			.firstNotNullOfOrNull { variationHeader -> variationHeader?.let { request.getHeader(it) } }

		val innsendingsIdPathVariable = variations
			.map { variation -> pathVariables[variation] }
			.firstNotNullOfOrNull { variationValue -> variationValue?.toString() }

		innsendingsIdHeader?.let {
			MDCUtil.toMDC(MDC_INNSENDINGS_ID, it)
		}

		innsendingsIdPathVariable?.let {
			MDCUtil.toMDC(MDC_INNSENDINGS_ID, it)
		}

		return true
	}


	override fun afterCompletion(
		request: HttpServletRequest,
		response: HttpServletResponse,
		handler: Any,
		ex: Exception?
	) {
		MDCUtil.clear()
	}
}
