package no.nav.soknad.innsending.security

import no.nav.security.token.support.core.context.TokenValidationContext
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.InvalidUserIdentityException
import no.nav.soknad.innsending.util.Constants.AZURE
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
		if (!tokenValidationContext.hasTokenFor(TOKENX)) {
			throw BackendErrorException("Autentisering kunne ikke fullføres")
		}

		val token = tokenValidationContext.getClaims(TOKENX)
		val pid = token.get(CLAIM_PID)
		return when (pid) {
			is String -> pid
			null -> token.subject ?: throw InvalidUserIdentityException()
			else -> throw InvalidUserIdentityException()
		}
	}

	override fun getToken(): String {
		return tokenValidationContext.getJwtToken(TOKENX)?.encodedToken
			?: throw BackendErrorException("Could not get tokenx token")
	}

	override fun getConsumerId(): String {
		return "srvinnsending-api"
	}


	override fun getClientId(): String {
		if (tokenValidationContext.hasTokenFor(AZURE)) {
			val claims = tokenValidationContext.getClaims(AZURE)
			// Task authorization requires azp separately; retain legacy identity handling on other Azure APIs.
			return claims.getStringClaim(AZP) ?: claims.getStringClaim(CLIENT_ID)
				?: throw BackendErrorException("Azure caller identity is missing")
		}
		val claims = tokenValidationContext.getClaims(TOKENX)
		return claims.getStringClaim(CLIENT_ID)
			?: claims.getStringClaim(AZP)
	}

	override fun getNavIdent(): String {
		tokenValidationContext.getClaims(AZURE).let { claims ->
			return claims.getStringClaim("NAVident")
				?: throw RuntimeException("Could not find NAVident claim in token")
		}
	}

	companion object {
		private const val CLAIM_PID = "pid"
		private const val CLIENT_ID = "client_id"
		private const val AZP = "azp"
	}
}
