package no.nav.soknad.innsending.config.security

import com.nimbusds.jose.jwk.JWK
import org.springframework.core.convert.converter.Converter
import org.springframework.security.oauth2.client.endpoint.NimbusJwtClientAuthenticationParametersConverter
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import java.time.Instant
import java.util.function.Function

// Client_credentials-registreringer mot Azure AD med client-authentication-method:
// private_key_jwt (kodeverk, saf-maskintilmaskin - se application.yml) signerer
// client_assertion med AZURE_APP_JWK. Spring sin innebygde client_credentials-provider
// (OAuth2AuthorizedClientProviderBuilder.clientCredentials()) støtter kun
// client_secret_basic/client_secret_post/none uten videre konfigurasjon, og kaster
// IllegalArgumentException for private_key_jwt hvis ikke accessTokenResponseClient
// customizes med en converter som håndterer det - se NimbusJwtClientAuthenticationParametersConverter.
//
// NimbusJwtClientAuthenticationParametersConverter.convert() returnerer null (no-op) for
// registreringer som IKKE bruker private_key_jwt/client_secret_jwt (f.eks. soknadsmottaker,
// som bruker client_secret_basic), så denne responsklienten er trygg å bruke for ALLE
// client_credentials-registreringer, ikke bare de Azure AD-baserte.
@Service
class AzureAdClientCredentialsConfig(
	private val azureAdProperties: AzureAdProperties
) {

	private val privateJwk: JWK by lazy { resolvePrivateJwk(azureAdProperties.privateJwk) }

	// 🔴 Rød sone: signering av client_assertion med privat nøkkel er sikkerhetskritisk.
	private val jwkResolver = Function<ClientRegistration, JWK> { privateJwk }

	val accessTokenResponseClient: OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> =
		RestClientClientCredentialsTokenResponseClient().apply {
			val nimbusConverter =
				NimbusJwtClientAuthenticationParametersConverter<OAuth2ClientCredentialsGrantRequest>(jwkResolver)
			// Azure AD krever, i likhet med TokenX, at client_assertion-JWT-en har en
			// "nbf"-claim (not-before) - se Microsoft sin dokumentasjon for "certificate
			// credentials". Spring setter ikke denne claimen som default.
			nimbusConverter.setJwtClientAssertionCustomizer { context ->
				context.claims.notBefore(Instant.now())
			}
			addParametersConverter(Converter<OAuth2ClientCredentialsGrantRequest, MultiValueMap<String, String>> { grantRequest ->
				nimbusConverter.convert(grantRequest) ?: LinkedMultiValueMap()
			})
		}
}
