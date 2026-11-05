package no.nav.soknad.innsending.consumerapis.token

import no.nav.soknad.innsending.security.TokenIssuer

/** Validerer innkommende token (#461). Implementert av Texas-sidecaren i dev og prod. */
interface TokenIntrospector {
	/** Kaster [TokenServiceUnavailableException] når valideringen ikke kan gjøres. */
	fun introspect(issuer: TokenIssuer, token: String): IntrospectionResult
}

/** Claimene skal aldri logges, så [toString] viser bare om tokenet er gyldig. */
class IntrospectionResult(val active: Boolean, val claims: Map<String, Any?>) {
	override fun toString(): String = "IntrospectionResult(active=$active)"
}
