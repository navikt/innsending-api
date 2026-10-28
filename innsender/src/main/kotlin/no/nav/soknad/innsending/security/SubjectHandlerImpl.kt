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

	override fun getNavIdent(): String {
		tokenValidationContext.getClaims(AZURE).let { claims ->
			return claims.getStringClaim(NAV_IDENT)
				?: throw RuntimeException("Could not find NAVident claim in token")
		}
	}

	override fun getAzureClientName(): String? =
		tokenValidationContext.getClaims(AZURE).getStringClaim(AZP_NAME)

	override fun getAzureUserIdent(): String? {
		val claims = tokenValidationContext.getClaims(AZURE)
		return claims.getStringClaim(NAV_IDENT)?.takeIf { it.isNotBlank() }
			?: claims.getStringClaim(PREFERRED_USERNAME)?.takeIf { it.isNotBlank() }
	}

	companion object {
		private const val CLAIM_PID = "pid"
		private const val CLIENT_ID = "client_id"
		private const val AZP = "azp"
		private const val AZP_NAME = "azp_name"
		private const val NAV_IDENT = "NAVident"
		private const val PREFERRED_USERNAME = "preferred_username"
	}
}
