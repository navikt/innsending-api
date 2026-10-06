package no.nav.soknad.innsending.config.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository


// Speiler @Profile-scopet til de reelle klient-konfigurasjonene (PdlClientConfig, SafClientConfig m.fl.):
// spring.security.oauth2.client.registration.* finnes kun for dev/prod/gcp (og test, se application-test.yml).
// Uten denne restriksjonen forsøker Spring å opprette denne beanen også under local/docker/endtoend,
// der ingen registreringer finnes - da feiler autowiring av ClientRegistrationRepository.
@Profile("test | prod | dev")
@Configuration
class OAuth2ClientManagerConfig(
	private val tokenExchangeService: TokenExchangeService,
	private val azureAdClientCredentialsConfig: AzureAdClientCredentialsConfig
) {

	// clientRegistrationRepository injiseres som parameter på @Bean-metoden (samme mønster som
	// RestClientOAuthConfig), ikke som konstruktørfelt. Siden beanen produseres implisitt av
	// Spring Boots betingede OAuth2ClientAutoConfiguration, klarer ikke IntelliJs "could not
	// autowire"-inspeksjon å resolve den ved konstruktør-injeksjon - kun ved metode-injeksjon.
	@Bean
	fun authorizedClientManager(
		clientRegistrationRepository: ClientRegistrationRepository
	): OAuth2AuthorizedClientManager {
		// Register support for client_credentials (inkl. private_key_jwt mot Azure AD -
		// se AzureAdClientCredentialsConfig) og vårt eget token-exchange-oppsett mot TokenX.
		val provider = OAuth2AuthorizedClientProviderBuilder.builder()
			.clientCredentials { it.accessTokenResponseClient(azureAdClientCredentialsConfig.accessTokenResponseClient) }
			.provider { context ->
				val grantType = context.clientRegistration.authorizationGrantType.value
				if (grantType == "urn:ietf:params:oauth:grant-type:jwt-bearer" || grantType == "urn:ietf:params:oauth:grant-type:token-exchange") {
					tokenExchangeService.performJwtBearerExchange(context)
				} else null
			}
			.build()

		val manager = AuthorizedClientServiceOAuth2AuthorizedClientManager(
			clientRegistrationRepository,
			InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository)
		)
		manager.setAuthorizedClientProvider(provider)
		return manager
	}
}

