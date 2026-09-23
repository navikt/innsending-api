package no.nav.soknad.innsending.config.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Konfigurasjon for token-exchange (RFC 8693) mot TokenX.
 *
 * privateJwk: enten selve JWK-en som JSON (slik NAIS injiserer TOKEN_X_PRIVATE_JWK i prod/dev),
 * eller en filsti til en JWK-fil (brukes i tester). Brukes til å signere client_assertion
 * (private_key_jwt) i token-exchange-kall mot TokenX.
 *
 * audiences: målet (audience) for token-exchange per registration-id i
 * spring.security.oauth2.client.registration.* (f.eks. "tokenx-pdl" -> PDL sin TokenX-audience).
 */
@ConfigurationProperties(prefix = "tokenx")
class TokenExchangeProperties {
	lateinit var privateJwk: String
	lateinit var audiences: Map<String, String>
}
