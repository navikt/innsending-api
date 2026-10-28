package no.nav.soknad.innsending.security

import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.soknad.innsending.util.Constants.TOKENX
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Gir innbyggerens innkommende TokenX-token, som byttes hos Texas mot token til tjenester vi kaller på vegne av innbyggeren.
 *
 * Bare TokenX-tokenet returneres. Kommer forespørselen med et Azure-token, finnes det ikke noe å bytte, og den som kaller
 * må håndtere det på samme måte som en feil mot tjenesten. Token-support byttet tidligere det første gyldige tokenet,
 * uansett issuer.
 *
 * Tokenet leses fra request-konteksten i tråden som kaller. Hent det før arbeid flyttes til en annen tråd.
 * I steg 3 i #461 byttes kilden fra token-support til Spring Security.
 */
interface BrukerTokenKilde {
	fun hentTokenXToken(): String?
}

@Component
@Profile("test | dev | prod")
class TokenSupportBrukerTokenKilde(private val contextHolder: TokenValidationContextHolder) : BrukerTokenKilde {
	override fun hentTokenXToken(): String? =
		contextHolder.getTokenValidationContext().getJwtToken(TOKENX)?.encodedToken
}

/** Forespørselen har ikke noe TokenX-token å bytte. Meldingen skal aldri inneholde token. */
class ManglerBrukerTokenException(message: String) : RuntimeException(message)

/** Henter TokenX-tokenet eller kaster [ManglerBrukerTokenException]. */
fun BrukerTokenKilde.krevTokenXToken(target: String): String =
	hentTokenXToken() ?: throw ManglerBrukerTokenException("Forespørselen har ikke noe TokenX-token å bytte for target=$target")
