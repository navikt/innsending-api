package no.nav.soknad.innsending.security

import no.nav.security.token.support.core.context.TokenValidationContext
import no.nav.security.token.support.jaxrs.JaxrsTokenValidationContextHolder
import no.nav.soknad.innsending.util.Constants.TOKENX
import no.nav.soknad.innsending.util.Constants.SELVBETJENING
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile

import org.springframework.stereotype.Component

@Component
@Profile("dev | prod")
class SubjectHandlerImpl : SubjectHandlerInterface {

	private val tokenValidationContext: TokenValidationContext
		get() {
			return JaxrsTokenValidationContextHolder.getHolder().tokenValidationContext
				?: throw RuntimeException("Could not find TokenValidationContext. Possibly no token in request.")
					.also { log.error("Could not find TokenValidationContext. Possibly no token in request and request was not captured by token-validation filters.") }
		}

	override fun getUserIdFromToken(): String {
		return when {
			tokenValidationContext.hasTokenFor(TOKENX) -> getUserIdFromTokenWithIssuer(TOKENX)
			else -> getUserIdFromTokenWithIssuer(SELVBETJENING)
		}
	}

	private fun getUserIdFromTokenWithIssuer(issuer: String): String {
		val pid: String? = tokenValidationContext.getClaims(issuer).getStringClaim(CLAIM_PID)
		val sub: String? = tokenValidationContext.getClaims(issuer).subject
		return pid ?: sub ?: throw RuntimeException("Could not find any userId for token in pid or sub claim")
	}

	override fun getToken(): String {
		return tokenValidationContext.getJwtToken(SELVBETJENING).tokenAsString
	}

	override fun getConsumerId(): String {
		return "srvsoknadsosialhje"
	}

	companion object {
		private const val CLAIM_PID = "pid"
		private val log = LoggerFactory.getLogger(javaClass)
	}
}
