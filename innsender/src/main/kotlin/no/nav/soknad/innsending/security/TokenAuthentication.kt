package no.nav.soknad.innsending.security

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

enum class TokenIssuer { TOKENX, AZURE }

/** Et validert token. Tokenet og claimene skal aldri logges, så [toString] viser ingen av dem. */
class ValidatedToken(val claims: Map<String, Any?>, val token: String) {
	fun stringClaim(name: String): String? = claims[name] as? String

	override fun toString(): String = "ValidatedToken"
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
class TokenAuthentication(
	val tokens: Map<TokenIssuer, ValidatedToken>,
) : AbstractAuthenticationToken(
	tokens.flatMap { (issuer, token) -> Access.authoritiesFor(issuer, token.claims) }.distinct().map(::SimpleGrantedAuthority)
) {

	constructor(issuer: TokenIssuer, claims: Map<String, Any?>, token: String) :
		this(mapOf(issuer to ValidatedToken(claims, token)))

	init {
		require(tokens.isNotEmpty()) { "TokenAuthentication krever minst ett validert token" }
		isAuthenticated = true
	}

	fun token(issuer: TokenIssuer): ValidatedToken? = tokens[issuer]

	override fun getCredentials(): String = (tokens[TokenIssuer.TOKENX] ?: tokens.values.first()).token

	override fun getPrincipal(): String = tokens.keys.joinToString(",") { it.name }

	override fun toString(): String =
		"TokenAuthentication(utstedere=${tokens.keys}, tilganger=${authorities.map { it.authority }})"

	override fun equals(other: Any?): Boolean = this === other

	override fun hashCode(): Int = System.identityHashCode(this)
}
