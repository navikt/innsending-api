package no.nav.soknad.innsending.interceptor

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import no.nav.soknad.innsending.util.Constants.HEADER_CALL_ID
import no.nav.soknad.innsending.util.Constants.MDC_INNSENDINGS_ID
import no.nav.soknad.innsending.util.MDCUtil
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping
import java.util.*

@Component
class MdcInterceptor : HandlerInterceptor {
	// Add values from headers or path variables to MDC
	override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
		val pathVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)
			?.let { it as Map<*, *> } ?: return true
		val headerNames = Collections.list(request.headerNames)

		val innsendindsIdvariations = arrayOf("x-innsendingid", "x-innsendingsid", "innsendingid", "innsendingsid")
		val callIdvariations = arrayOf("x-nav-call-id", "nav-call-id", "call-id")

		val innsendingsIdPathVariable = pathVariables
			.filterKeys { key ->
				innsendindsIdvariations.any { variation ->
					key.toString().equals(variation, ignoreCase = true)
				}
			}
			.values
			.firstOrNull { it != null }?.toString()

		val innsendingsIdHeader = innsendindsIdvariations
			.map { variation -> headerNames.find { it.equals(variation, ignoreCase = true) } }
			.firstNotNullOfOrNull { variationHeader -> variationHeader?.let { request.getHeader(it) } }

		val callIdHeader = callIdvariations
			.map { variation -> headerNames.find { it.equals(variation, ignoreCase = true) } }
			.firstNotNullOfOrNull { variationHeader -> variationHeader?.let { request.getHeader(it) } }

		innsendingsIdPathVariable?.let {
			MDCUtil.toMDC(MDC_INNSENDINGS_ID, it)
		}

		innsendingsIdHeader?.let {
			MDCUtil.toMDC(MDC_INNSENDINGS_ID, it)
		}

		callIdHeader?.let {
			MDCUtil.toMDC(HEADER_CALL_ID, it)
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
