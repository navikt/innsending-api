package no.nav.soknad.innsending.filter

import no.nav.soknad.innsending.util.Constants.CORRELATION_ID
import no.nav.soknad.innsending.util.Constants.HEADER_CALL_ID
import no.nav.soknad.innsending.util.Constants.NAV_CONSUMER_ID
import no.nav.soknad.innsending.util.MDCUtil
import org.springframework.beans.factory.annotation.Value
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerRequestFilter
import javax.ws.rs.ext.Provider

@Provider
class MdcFilter(@Value("\$spring.application.name}") private val applicationName: String): ContainerRequestFilter {

	override fun filter(requestContext: ContainerRequestContext) {

		MDCUtil.toMDC(NAV_CONSUMER_ID, requestContext.getHeaderString(NAV_CONSUMER_ID), applicationName)
		MDCUtil.toMDC(HEADER_CALL_ID, requestContext.getHeaderString(HEADER_CALL_ID),  MDCUtil.callIdOrNew())
		MDCUtil.toMDC(CORRELATION_ID, requestContext.getHeaderString(CORRELATION_ID), MDCUtil.callIdOrNew())

	}

}
