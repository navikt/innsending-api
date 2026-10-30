package no.nav.soknad.innsending.security

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

enum class TokenUtsteder {
	TOKENX, AZURE;

	companion object {
		/** Leser en kommaseparert liste, for eksempel verdien av texas.innkommende.utstedere. */
		fun fraListe(verdi: String): Set<TokenUtsteder> =
			verdi.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { valueOf(it.uppercase()) }.toSet()
	}
}

/** Et validert token. Tokenet og claimene skal aldri logges, så [toString] viser ingen av dem. */
class ValidertToken(val claims: Map<String, Any?>, val token: String) {
	fun stringClaim(navn: String): String? = claims[navn] as? String

	override fun toString(): String = "ValidertToken"
}

/**
 * De validerte innkommende tokenene i Spring Security, per utsteder.
 *
 * Vanligvis har forespørselen ett bearer-token. Token-support godtar også flere kommaseparerte token i samme header,
 * og da gjelder tilgangene fra alle. Broen beholder det i steg 3a–3h, slik at oppførselen ikke endres.
 *
 * Tokenene og claimene skal aldri logges. [toString] viser derfor bare utstedere og tilganger, og [getPrincipal] gir
 * utstedere, ikke identitet. Identitet leses gjennom [SubjectHandlerInterface].
 */
class TokenAutentisering(
	val tokens: Map<TokenUtsteder, ValidertToken>,
) : AbstractAuthenticationToken(
	tokens.flatMap { (utsteder, token) -> Tilgang.tilgangerFor(utsteder, token.claims) }.distinct().map(::SimpleGrantedAuthority)
) {

	constructor(utsteder: TokenUtsteder, claims: Map<String, Any?>, token: String) :
		this(mapOf(utsteder to ValidertToken(claims, token)))

	init {
		require(tokens.isNotEmpty()) { "TokenAutentisering krever minst ett validert token" }
		isAuthenticated = true
	}

	fun token(utsteder: TokenUtsteder): ValidertToken? = tokens[utsteder]

	override fun getCredentials(): String = (tokens[TokenUtsteder.TOKENX] ?: tokens.values.first()).token

	override fun getPrincipal(): String = tokens.keys.joinToString(",") { it.name }

	override fun toString(): String =
		"TokenAutentisering(utstedere=${tokens.keys}, tilganger=${authorities.map { it.authority }})"

	override fun equals(other: Any?): Boolean = this === other

	override fun hashCode(): Int = System.identityHashCode(this)
}
