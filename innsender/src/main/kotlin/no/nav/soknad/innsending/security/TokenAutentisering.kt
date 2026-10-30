package no.nav.soknad.innsending.security

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

enum class TokenUtsteder { TOKENX, AZURE }

/**
 * Et validert innkommende token i Spring Security.
 *
 * Tokenet og claimene skal aldri logges. [toString] viser derfor bare utsteder og tilganger, og [getPrincipal] gir
 * utsteder, ikke identitet. Identitet leses gjennom [SubjectHandlerInterface].
 */
class TokenAutentisering(
	val utsteder: TokenUtsteder,
	val claims: Map<String, Any?>,
	private val token: String,
) : AbstractAuthenticationToken(Tilgang.tilgangerFor(utsteder, claims).map(::SimpleGrantedAuthority)) {

	init {
		isAuthenticated = true
	}

	override fun getCredentials(): String = token

	override fun getPrincipal(): String = utsteder.name

	fun stringClaim(navn: String): String? = claims[navn] as? String

	override fun toString(): String = "TokenAutentisering(utsteder=$utsteder, tilganger=${authorities.map { it.authority }})"

	override fun equals(other: Any?): Boolean = this === other

	override fun hashCode(): Int = System.identityHashCode(this)
}
