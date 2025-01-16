package no.nav.soknad.innsending.security

import no.nav.security.token.support.core.context.TokenValidationContext
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.util.Constants.AZURE
import no.nav.soknad.innsending.util.Constants.SELVBETJENING
import no.nav.soknad.innsending.util.Constants.TOKENX
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
			return ctxHolder.getTokenValidationContext()
		}

	override fun getUserIdFromToken(): String {
		return when {
			tokenValidationContext.hasTokenFor(TOKENX) -> getUserIdFromTokenWithIssuer(TOKENX)
			else -> getUserIdFromTokenWithIssuer(SELVBETJENING)
		}
	}

	private fun getUserIdFromTokenWithIssuer(issuer: String): String {
		val token = tokenValidationContext.getClaims(issuer)
		val pid: String? = token.getStringClaim(CLAIM_PID)
		val sub: String? = token.subject
		return pid ?: sub ?: throw RuntimeException("Could not find any userId for token in pid or sub claim")
	}

	override fun getToken(): String {
		return tokenValidationContext.getJwtToken(TOKENX)?.encodedToken
			?: throw BackendErrorException("Could not get tokenx token")
	}

	override fun getConsumerId(): String {
		return "srvinnsending-api"
	}


	// The MockOAuth2Server sets AZP claim for the client id, while the real tokens uses client_id claim
	override fun getClientId(): String {
		val claims = try {
			tokenValidationContext.getClaims(TOKENX)
		} catch (e: Exception) {
			tokenValidationContext.getClaims(AZURE)
		}
		return claims.getStringClaim(CLIENT_ID)
			?: claims.getStringClaim(AZP)
	}

	companion object {
		private const val CLAIM_PID = "pid"
		private const val CLIENT_ID = "client_id"
		private const val AZP = "azp"
	}
}

