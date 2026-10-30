package no.nav.soknad.innsending.security

import org.springframework.context.annotation.Profile
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * Gir innbyggerens innkommende TokenX-token, som byttes hos Texas mot token til tjenester vi kaller på vegne av innbyggeren.
 *
 * Bare TokenX-tokenet returneres. Kommer forespørselen med et Azure-token, finnes det ikke noe å bytte, og den som kaller
 * må håndtere det på samme måte som en feil mot tjenesten.
 *
 * Tokenet leses fra sikkerhetskonteksten i tråden som kaller. Hent det før arbeid flyttes til en annen tråd.
 */
interface BrukerTokenKilde {
	fun hentTokenXToken(): String?
}

/**
 * Leser TokenX-tokenet fra Spring Security. Utenfor en HTTP-forespørsel finnes ingen autentisering, og da gis null.
 */
@Component
@Profile("test | dev | prod")
class SpringSecurityBrukerTokenKilde : BrukerTokenKilde {
	override fun hentTokenXToken(): String? =
		(SecurityContextHolder.getContext().authentication as? TokenAutentisering)
			?.token(TokenUtsteder.TOKENX)
			?.token
}

/** Forespørselen har ikke noe TokenX-token å bytte. Meldingen skal aldri inneholde token. */
class ManglerBrukerTokenException(message: String) : RuntimeException(message)

/** Henter TokenX-tokenet eller kaster [ManglerBrukerTokenException]. */
fun BrukerTokenKilde.krevTokenXToken(target: String): String =
	hentTokenXToken() ?: throw ManglerBrukerTokenException("Forespørselen har ikke noe TokenX-token å bytte for target=$target")
