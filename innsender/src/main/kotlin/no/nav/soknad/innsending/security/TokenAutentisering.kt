package no.nav.soknad.innsending.security

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

enum class TokenUtsteder { TOKENX, AZURE }

/** Et validert token. Tokenet og claimene skal aldri logges, så [toString] viser ingen av dem. */
class ValidertToken(val claims: Map<String, Any?>, val token: String) {
	fun stringClaim(navn: String): String? = claims[navn] as? String

	override fun toString(): String = "ValidertToken"
}

/**
 * De validerte innkommende tokenene i Spring Security, per utsteder.
 *
 * Texas validerer ett token per forespørsel, så det er ett token her. Strukturen tillater flere, med tilgangene fra
 * alle, slik token-support gjorde.
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
