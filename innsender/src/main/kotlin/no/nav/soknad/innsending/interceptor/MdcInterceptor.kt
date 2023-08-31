package no.nav.soknad.innsending.interceptor

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import no.nav.soknad.innsending.util.Constants.MDC_INNSENDINGS_ID
import no.nav.soknad.innsending.util.MDCUtil
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping

@Component
class MdcInterceptor : HandlerInterceptor {
	override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
		val pathVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as Map<*, *>

		request.getHeader("X-innsendingId")?.let {
			MDCUtil.toMDC(MDC_INNSENDINGS_ID, it)
		}

		pathVariables["innsendingsId"]?.let {
			MDCUtil.toMDC(MDC_INNSENDINGS_ID, it.toString())
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
