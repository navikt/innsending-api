package no.nav.soknad.innsending.interceptor

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import no.nav.soknad.innsending.util.Constants.CORRELATION_ID
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
		findInnsendingsId(request)?.let {
			MDCUtil.toMDC(MDC_INNSENDINGS_ID, it)
		}

		findCallId(request)?.let {
			MDCUtil.toMDC(HEADER_CALL_ID, it)
			MDCUtil.toMDC(CORRELATION_ID, it)
		}

		return true
	}

	private fun findInnsendingsId(request: HttpServletRequest): String? {
		val innsendindsIdvariations = arrayOf("x-innsendingid", "x-innsendingsid", "innsendingid", "innsendingsid")

		// Try headers first
		val headerNames = Collections.list(request.headerNames)
		val fromHeader = innsendindsIdvariations
			.map { variation -> headerNames.find { it.equals(variation, ignoreCase = true) } }
			.firstNotNullOfOrNull { variationHeader -> variationHeader?.let { request.getHeader(it) } }

		if (fromHeader != null) return fromHeader

		// Then try path variables
		val pathVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)
			?.let { it as Map<*, *> } ?: emptyMap<String, Any>()

		val fromPath = pathVariables
			.filterKeys { key ->
				innsendindsIdvariations.any { variation ->
					key.toString().equals(variation, ignoreCase = true)
				}
			}
			.values
			.firstOrNull { it != null }?.toString()

		if (fromPath != null) return fromPath

		// Finally try request parameters
		return request.parameterMap.entries
			.firstOrNull { (key, _) -> key == "innsendingId" }
			?.value?.firstOrNull()
	}

	private fun findCallId(request: HttpServletRequest): String? {
		val callIdvariations = arrayOf("x-nav-call-id", "nav-call-id", "call-id", "x-correlation-id", "correlation-id")
		val headerNames = Collections.list(request.headerNames)

		return callIdvariations
			.map { variation -> headerNames.find { it.equals(variation, ignoreCase = true) } }
			.firstNotNullOfOrNull { variationHeader -> variationHeader?.let { request.getHeader(it) } }
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
