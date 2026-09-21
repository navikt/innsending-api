package no.nav.soknad.innsending.config.security

import com.nimbusds.jose.jwk.JWK
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.TokenExchangeOAuth2AuthorizedClientProvider
import org.springframework.security.oauth2.client.endpoint.NimbusJwtClientAuthenticationParametersConverter
import org.springframework.security.oauth2.client.endpoint.RestClientTokenExchangeTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.TokenExchangeGrantRequest
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import java.time.Instant
import java.util.function.Function

// Utfører token-exchange (RFC 8693) mot TokenX for registreringer med
// authorization-grant-type: urn:ietf:params:oauth:grant-type:token-exchange
// (tokenx-pdl, tokenx-safselvbetjening, kontoregister, arena - se application.yml).
//
// Tidligere versjon bygde token-requesten manuelt med det gamle NAV STS-formatet
// (grant_type=jwt-bearer, assertion, requested_token_use=on_behalf_of, client_secret),
// og sendte aldri client_assertion/client_assertion_type - derfor feilet TokenX med
// "Parameter client_assertion_type missing". Nå brukes Spring Security sine innebygde
// klasser for token-exchange og private_key_jwt-signering.
@Service
class TokenExchangeService(
	private val tokenExchangeProperties: TokenExchangeProperties
) {
	private val log = LoggerFactory.getLogger(javaClass)

	// TOKEN_X_PRIVATE_JWK er den samme private nøkkelen for alle TokenX-registreringer,
	// så den kan trygt caches og gjenbrukes på tvers av registration-id.
	private val privateJwk: JWK by lazy { resolvePrivateJwk(tokenExchangeProperties.privateJwk) }

	// 🔴 Rød sone: signering av client_assertion med privat nøkkel er sikkerhetskritisk.
	// Se javadoc for NimbusJwtClientAuthenticationParametersConverter for detaljer om hvordan
	// JWT-en (client_assertion) bygges og signeres (RFC 7523).
	private val jwkResolver = Function<ClientRegistration, JWK> { privateJwk }

	private val accessTokenResponseClient = RestClientTokenExchangeTokenResponseClient().apply {
		// Legger til client_assertion + client_assertion_type (private_key_jwt) på requesten.
		// Delegeres via egen lambda for å unngå at Kotlin ikke klarer å utlede/matche det
		// generiske Converter-supertypet til NimbusJwtClientAuthenticationParametersConverter
		// direkte (den bruker T kun i implements-klausulen, ikke i konstruktøren).
		val nimbusConverter = NimbusJwtClientAuthenticationParametersConverter<TokenExchangeGrantRequest>(jwkResolver)
		// TokenX validerer client_assertion-JWT-en strengt og krever bl.a. "nbf" (not-before) -
		// en claim Spring ikke setter som default (kun iss/sub/aud/exp/iat/jti). Uten denne
		// svarer TokenX med "JWT missing required claims: [nbf]".
		nimbusConverter.setJwtClientAssertionCustomizer { context ->
			context.claims.notBefore(Instant.now())
		}
		addParametersConverter(Converter<TokenExchangeGrantRequest, MultiValueMap<String, String>> { grantRequest ->
			nimbusConverter.convert(grantRequest) ?: LinkedMultiValueMap()
		})
		// TokenX krever i tillegg parameteren "audience" (målsystemet), som ikke er en del av
		// standard RFC 8693-parametrene Spring setter automatisk.
		addParametersConverter(Converter<TokenExchangeGrantRequest, MultiValueMap<String, String>> { grantRequest ->
			audienceParameters(grantRequest)
		})
	}

	private val tokenExchangeProvider = TokenExchangeOAuth2AuthorizedClientProvider().apply {
		setAccessTokenResponseClient(accessTokenResponseClient)
	}

	fun performJwtBearerExchange(context: OAuth2AuthorizationContext): OAuth2AuthorizedClient? {
		val registration = context.clientRegistration
		log.info(
			"Exchange token using ${registration.authorizationGrantType.value} for ${registration.registrationId} and scope ${
				registration.scopes.joinToString(" ")
			}"
		)

		val authorizedClient = tokenExchangeProvider.authorize(context)

		if (authorizedClient != null) {
			log.info("Exchange token successful for ${registration.registrationId}")
		}

		return authorizedClient
	}

	private fun audienceParameters(grantRequest: TokenExchangeGrantRequest): MultiValueMap<String, String> {
		val registrationId = grantRequest.clientRegistration.registrationId
		val audience = tokenExchangeProperties.audiences[registrationId]
			?: error("Fant ingen konfigurert TokenX-audience for registration-id '$registrationId'. Sjekk tokenx.audiences i application.yml.")

		return LinkedMultiValueMap<String, String>().apply {
			add("audience", audience)
		}
	}

}
