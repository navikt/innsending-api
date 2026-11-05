package no.nav.soknad.innsending.security

import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.soknad.innsending.util.Constants.TOKENX
import org.springframework.context.annotation.Profile
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * Gir innbyggerens innkommende TokenX-token, som byttes hos Texas mot token til tjenester vi kaller på vegne av innbyggeren.
 *
 * Bare TokenX-tokenet returneres. Kommer forespørselen med et Azure-token, finnes det ikke noe å bytte, og den som kaller
 * må håndtere det på samme måte som en feil mot tjenesten. Token-support byttet tidligere det første gyldige tokenet,
 * uansett issuer.
 *
 * Tokenet leses fra sikkerhetskonteksten i tråden som kaller. Hent det før arbeid flyttes til en annen tråd.
 */
interface UserTokenSource {
	fun getUserToken(): String?
}

/**
 * Leser TokenX-tokenet fra Spring Security (#461, steg 3f). Utenfor en HTTP-forespørsel finnes ingen autentisering,
 * og da gis null.
 */
@Component
@Profile("test | dev | prod")
class SpringSecurityUserTokenSource : UserTokenSource {
	override fun getUserToken(): String? =
		(SecurityContextHolder.getContext().authentication as? TokenAuthentication)
			?.token(TokenIssuer.TOKENX)
			?.token
}

/** Kilden før steg 3f. Ikke i bruk. Fjernes med token-support i steg 4. */
class TokenSupportUserTokenSource(private val contextHolder: TokenValidationContextHolder) : UserTokenSource {
	override fun getUserToken(): String? {
		val context = try {
			contextHolder.getTokenValidationContext()
		} catch (_: IllegalStateException) {
			// Kalt utenfor en HTTP-forespørsel, for eksempel fra en jobb. Da finnes det ikke noe innbyggertoken
			return null
		}
		return context.getJwtToken(TOKENX)?.encodedToken
	}
}

/** Forespørselen har ikke noe TokenX-token å bytte. Meldingen skal aldri inneholde token. */
class MissingUserTokenException(message: String) : RuntimeException(message)

/** Henter TokenX-tokenet eller kaster [MissingUserTokenException]. */
fun UserTokenSource.requireUserToken(target: String): String =
	getUserToken() ?: throw MissingUserTokenException("Forespørselen har ikke noe TokenX-token å bytte for target=$target")
