package no.nav.soknad.innsending.config.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Konfigurasjon for signering av client_assertion (private_key_jwt) mot Azure AD.
 *
 * privateJwk: enten selve JWK-en som JSON (slik NAIS injiserer AZURE_APP_JWK i prod/dev),
 * eller en filsti til en JWK-fil (brukes i tester). Brukes av
 * AzureAdClientCredentialsConfig for client_credentials-registreringer som bruker
 * client-authentication-method: private_key_jwt (kodeverk, saf-maskintilmaskin).
 */
@ConfigurationProperties(prefix = "azuread")
class AzureAdProperties {
	lateinit var privateJwk: String
}
