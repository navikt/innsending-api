package no.nav.soknad.innsending.security

import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.InvalidUserIdentityException
import org.springframework.context.annotation.Profile
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * Leser identiteten fra Spring Security (#461, steg 3f).
 *
 * Gir samme svar som [SubjectHandlerImpl] ga med token-support. Autentiseringen kommer fra broen i steg 3a og senere
 * fra Texas, så denne klassen trenger ikke endres når valideringen byttes.
 */
@Component
@Profile("test | dev | prod | endtoend-auth")
class SpringSecuritySubjectHandler : SubjectHandlerInterface {

	private fun autentisering(): TokenAutentisering? =
		SecurityContextHolder.getContext().authentication as? TokenAutentisering

	private fun tokenX(): ValidertToken? = autentisering()?.token(TokenUtsteder.TOKENX)

	override fun getUserIdFromToken(): String {
		val tokenX = tokenX() ?: throw BackendErrorException("Autentisering kunne ikke fullføres")

		return when (val pid = tokenX.claims[CLAIM_PID]) {
			is String -> pid
			null -> tokenX.stringClaim(CLAIM_SUB) ?: throw InvalidUserIdentityException()
			else -> throw InvalidUserIdentityException()
		}
	}

	override fun getToken(): String =
		tokenX()?.token ?: throw BackendErrorException("Could not get tokenx token")

	override fun getConsumerId(): String = "srvinnsending-api"

	// TokenX først, ellers Azure, som før. MockOAuth2Server setter klient-ID i azp, mens ekte token bruker client_id
	override fun getClientId(): String {
		val autentisering = autentisering() ?: throw BackendErrorException("Autentisering kunne ikke fullføres")
		val token = autentisering.token(TokenUtsteder.TOKENX) ?: autentisering.token(TokenUtsteder.AZURE)
			?: throw BackendErrorException("Autentisering kunne ikke fullføres")
		return token.stringClaim(CLIENT_ID)
			?: token.stringClaim(AZP)
			?: throw BackendErrorException("Fant ikke klient-ID i tokenet")
	}

	override fun getNavIdent(): String {
		val azure = autentisering()?.token(TokenUtsteder.AZURE)
			?: throw RuntimeException("Could not find NAVident claim in token")
		return azure.stringClaim("NAVident") ?: throw RuntimeException("Could not find NAVident claim in token")
	}

	private companion object {
		const val CLAIM_PID = "pid"
		const val CLAIM_SUB = "sub"
		const val CLIENT_ID = "client_id"
		const val AZP = "azp"
	}
}
