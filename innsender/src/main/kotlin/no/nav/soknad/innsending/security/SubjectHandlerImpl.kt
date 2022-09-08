package no.nav.soknad.innsending.security

import no.nav.security.token.support.core.context.TokenValidationContext
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.soknad.innsending.util.Constants.TOKENX
import no.nav.soknad.innsending.util.Constants.SELVBETJENING
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile

import org.springframework.stereotype.Component

@Component
@Profile("test | dev | prod")
class SubjectHandlerImpl(private val ctxHolder: TokenValidationContextHolder) : SubjectHandlerInterface {

	private val logger: Logger = LoggerFactory.getLogger(javaClass)

	private val tokenValidationContext: TokenValidationContext
		get() {
			return ctxHolder.tokenValidationContext
				?: throw RuntimeException("Could not find TokenValidationContext. Possibly no token in request.")
					.also { logger.error("Could not find TokenValidationContext. Possibly no token in request and request was not captured by token-validation filters.") }
		}

	override fun getUserIdFromToken(): String {
		return when {
			tokenValidationContext.hasTokenFor(TOKENX) -> getUserIdFromTokenWithIssuer(TOKENX)
			else -> getUserIdFromTokenWithIssuer(SELVBETJENING)
		}
	}

	private fun getUserIdFromTokenWithIssuer(issuer: String): String {
		val token = tokenValidationContext.getClaims(issuer)
		val pid: String? =  token?.getStringClaim(CLAIM_PID)
		val sub: String? = token?.subject
		return pid ?: sub ?: throw RuntimeException("Could not find any userId for token in pid or sub claim")
	}

	override fun getToken(): String {
		return tokenValidationContext.getJwtToken(TOKENX).tokenAsString
	}

	override fun getConsumerId(): String {
		return "srvinnsending-api"
	}

	companion object {
		private const val CLAIM_PID = "pid"
	}
}

