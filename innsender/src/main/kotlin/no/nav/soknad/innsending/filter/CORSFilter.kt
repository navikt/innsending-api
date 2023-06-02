package no.nav.soknad.innsending.filter

import jakarta.servlet.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.IOException

@Component
class CORSFilter : Filter {
	override fun init(filterChain: FilterConfig) {
	}

	val logger: Logger = LoggerFactory.getLogger(this.javaClass)

	override fun doFilter(servletRequest: ServletRequest, servletResponse: ServletResponse, filterChain: FilterChain) {
		val httpServletResponse: HttpServletResponse = servletResponse as HttpServletResponse
		val httpServletRequest: HttpServletRequest = servletRequest as HttpServletRequest

		httpServletResponse.setHeader("Access-Control-Allow-Origin", "*")
		httpServletResponse.setHeader("Access-Control-Allow-Methods", "POST, GET, DELETE, PATCH, OPTIONS")
		httpServletResponse.setHeader(
			"Access-Control-Allow-Headers",
			"accept, authorization, X-requested-with, content-type"
		)

		if (httpServletRequest.method == "OPTIONS")
			try {
				httpServletResponse.status = 200
				httpServletResponse.writer.print("OK")
				httpServletResponse.writer.flush()
			} catch (e: IOException) {
				logger.error("Feil ved filter, ${e.message}")
			}
		else
			filterChain.doFilter(servletRequest, servletResponse)
	}

	override fun destroy() {
	}
}
