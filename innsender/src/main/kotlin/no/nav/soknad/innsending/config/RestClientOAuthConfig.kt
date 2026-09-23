package no.nav.soknad.innsending.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.client.*
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class RestClientOAuthConfig(
	@Value("\${spring.application.name}") private val applicationName: String,
) {

	val logger: Logger = LoggerFactory.getLogger(javaClass)

	private val defaultReadTimeout: Long = 1 // minutes
	private val defaultConnectTimeout: Long = 20 // seconds
	private val defaultExchangeTimeout: Long = 2 // minutes

	@Bean
	@Qualifier("antivirusRestClient")
	fun antivirusRestClient(restConfig: RestConfig): RestClient {
		return RestClient.builder()
			.baseUrl(restConfig.antivirusUrl)
			.requestFactory(timeouts(readTimeoutMinutes = 2))
			.build()
	}

	@Bean
	@Profile("prod | dev")
	@Qualifier("arenaApiRestClient")
	fun arenaApiClient(
		authorizedClientManager: OAuth2AuthorizedClientManager,
		clientRegistrationRepository: ClientRegistrationRepository,
		restConfig: RestConfig,
	): RestClient {
		val oauth2Interceptor =
			createOauth2Interceptor(authorizedClientManager, "arena", clientRegistrationRepository)
		return RestClient.builder()
			.baseUrl(restConfig.arenaUrl)
			.requestFactory(timeouts())
			.requestInterceptor(oauth2Interceptor)
			.build()
	}

	@Bean
	@Profile("!(prod | dev)")
	@Qualifier("arenaApiRestClient")
	fun arenaApiClientWithoutAuth(restConfig: RestConfig) = RestClient.builder().baseUrl(restConfig.arenaUrl).build()


	@Bean
	@Profile("prod | dev")
	@Qualifier("kodeverkApiClient")
	fun kodeverkApiClient(
		authorizedClientManager: OAuth2AuthorizedClientManager,
		clientRegistrationRepository: ClientRegistrationRepository,
		restConfig: RestConfig,
	): RestClient {
		val oauth2Interceptor =
			createOauth2Interceptor(authorizedClientManager, "kodeverk", clientRegistrationRepository)
		return RestClient.builder()
			.baseUrl(restConfig.kodeverkUrl)
			.requestFactory(timeouts())
			.requestInterceptor(oauth2Interceptor)
			.build()
	}

	@Bean
	@Profile("!(prod | dev)")
	@Qualifier("kodeverkApiClient")
	fun kodeverkApiClientWithoutAuth(
		restConfig: RestConfig
	): RestClient {
		return RestClient.builder().baseUrl(restConfig.kodeverkUrl).build()
	}

	@Bean
	@Profile("prod | dev")
	@Qualifier("kontoregisterApiRestClient")
	fun kontoregisterApiClient(
		authorizedClientManager: OAuth2AuthorizedClientManager,
		clientRegistrationRepository: ClientRegistrationRepository,
		restConfig: RestConfig,
	): RestClient {
		val oauth2Interceptor =
			createOauth2Interceptor(authorizedClientManager, "kontoregister", clientRegistrationRepository)
		return RestClient.builder()
			.baseUrl(restConfig.kontoregisterUrl + "/api/borger")
			.requestFactory(timeouts())
			.requestInterceptor(oauth2Interceptor)
			.build()
	}

	@Bean
	@Profile("!(prod | dev)")
	@Qualifier("kontoregisterApiRestClient")
	fun kontoregisterApiClientWithoutAuth(restConfig: RestConfig) =
		RestClient.builder().baseUrl(restConfig.kontoregisterUrl + "/api/borger").build()

	@Bean
	@Profile("prod | dev")
	@Qualifier("soknadsmottakerRestClient")
	fun soknadsmottakerRestClient(
		authorizedClientManager: OAuth2AuthorizedClientManager,
		clientRegistrationRepository: ClientRegistrationRepository,
		restConfig: RestConfig,
	): RestClient {
		val oauth2Interceptor =
			createOauth2Interceptor(authorizedClientManager, "soknadsmottaker", clientRegistrationRepository)
		return RestClient.builder()
			.baseUrl(restConfig.soknadsMottakerHost)
			.requestFactory(timeouts())
			.requestInterceptor(oauth2Interceptor)
			.build()
	}

	@Bean
	@Profile("!(prod | dev)")
	@Qualifier("soknadsmottakerRestClient")
	fun soknadsmottakerClientWithoutOAuth(restConfig: RestConfig) =
		RestClient.builder().baseUrl(restConfig.soknadsMottakerHost).build()

	@Bean
	@Qualifier("skjemaRestClient")
	fun skjemaClientWithoutOAuth(restConfig: RestConfig) = RestClient.builder().baseUrl(restConfig.sanityHost).build()

	private fun timeouts(
		readTimeoutMinutes: Long = defaultReadTimeout,
		connectTimeoutSeconds: Long = defaultConnectTimeout
	): ClientHttpRequestFactory {
		val factory =
			SimpleClientHttpRequestFactory()  // MERK: støtter ikke http.patch bruk eventuelt JdkClientHttpRequestFactory
		factory.setReadTimeout(Duration.ofMinutes(readTimeoutMinutes))
		factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
		return factory
	}

	/**
	 * Privat hjelpemetode for å lage en gjenbrukbar interceptor.
	 * Denne metoden fungerer for både 'jwt-bearer' (som krever en bruker-principal)
	 * og 'client_credentials' (som ikke krever det).
	 */
	private fun createOauth2Interceptor(
		authorizedClientManager: OAuth2AuthorizedClientManager,
		clientRegistrationId: String,
		clientRegistrationRepository: ClientRegistrationRepository
	): ClientHttpRequestInterceptor {
		return ClientHttpRequestInterceptor { request, body, execution ->
			logger.info("createOauth2Interceptor for clientRegistrationId: $clientRegistrationId")
			val clientRegistration = clientRegistrationRepository.findByRegistrationId(clientRegistrationId)
				?: throw IllegalStateException("Fant ikke klient-registrering for '$clientRegistrationId'.")

			val authorizeRequestBuilder = OAuth2AuthorizeRequest.withClientRegistrationId(clientRegistrationId)

			if (clientRegistration.authorizationGrantType == AuthorizationGrantType.CLIENT_CREDENTIALS) {
				// ✅ For machine-to-machine flow, just use a static principal name
				authorizeRequestBuilder.principal("m2m-service-account")
			} else {
				// ✅ For OBO (JWT-bearer), forward the current authenticated user
				val principal = SecurityContextHolder.getContext().authentication
					?: throw IllegalStateException("Ingen SecurityContext Authentication funnet for OBO flyt.")
				authorizeRequestBuilder.principal(principal)
			}

			val authorizeRequest = authorizeRequestBuilder.build()

			val authorizedClient = authorizedClientManager.authorize(authorizeRequest)
				?: throw IllegalStateException(
					"Kunne ikke autorisere klienten '$clientRegistrationId'. " +
						"Sjekk konfigurasjon og grant-type."
				)

			request.headers.setBearerAuth(authorizedClient.accessToken.tokenValue)
			execution.execute(request, body)
		}
	}

}
