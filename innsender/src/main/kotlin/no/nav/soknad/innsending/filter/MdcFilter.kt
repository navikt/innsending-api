package no.nav.soknad.innsending.filter

import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.ext.Provider
import no.nav.soknad.innsending.util.Constants.CORRELATION_ID
import no.nav.soknad.innsending.util.Constants.HEADER_CALL_ID
import no.nav.soknad.innsending.util.Constants.NAV_CONSUMER_ID
import no.nav.soknad.innsending.util.MDCUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value

@Provider
class MdcFilter(@Value("\$spring.application.name}") private val applicationName: String) : ContainerRequestFilter {
	val logger: Logger = LoggerFactory.getLogger(this.javaClass)

	override fun filter(requestContext: ContainerRequestContext) {

		logger.info("Kjører MdcFilter")
		MDCUtil.toMDC(NAV_CONSUMER_ID, requestContext.getHeaderString(NAV_CONSUMER_ID), applicationName)
		MDCUtil.toMDC(HEADER_CALL_ID, requestContext.getHeaderString(HEADER_CALL_ID), MDCUtil.callIdOrNew())
		MDCUtil.toMDC(CORRELATION_ID, requestContext.getHeaderString(CORRELATION_ID), MDCUtil.callIdOrNew())

	}

}
