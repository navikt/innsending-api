package no.nav.soknad.innsending.config

import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.soknad.arkivering.soknadsarkiverer.service.tokensupport.TokenService
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.MDCUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpRequest
import org.springframework.http.client.*
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class RestClientOAuthConfig(
	@Value("\${spring.application.name}") private val applicationName: String,
	) {

	val logger: Logger = LoggerFactory.getLogger(javaClass)

	@Bean
	@Profile("prod | dev")
	@Qualifier("arenaApiRestClient")
	fun arenaApiClient(
		restConfig: RestConfig,
		clientConfigProperties: ClientConfigurationProperties,
		oAuth2AccessTokenService: OAuth2AccessTokenService,
		subjectHandler: SubjectHandlerInterface
	) = arenaClient(restConfig.arenaUrl, clientConfigProperties.registration["arena"]!!, oAuth2AccessTokenService, subjectHandler)

	private fun arenaClient(
		baseUrl: String,
		clientProperties: ClientProperties,
		oAuth2AccessTokenService: OAuth2AccessTokenService,
		subjectHandler: SubjectHandlerInterface
	): RestClient {

		val tokenService = TokenService(clientProperties, oAuth2AccessTokenService)

		return RestClient.builder()
			.baseUrl(baseUrl)
			.requestFactory(timeouts())
			.requestInterceptor(AddRequestHeaders(tokenService, subjectHandler))
			.build()
	}

	@Bean
	@Profile("!(prod | dev)")
	@Qualifier("arenaApiRestClient")
	fun arenaApiClientWithoutAuth(restConfig: RestConfig) = RestClient.builder().baseUrl(restConfig.arenaUrl).build()


	@Bean
	@Profile("prod | dev")
	@Qualifier("azureApiRestClient")
	fun azureApiClient(
		restConfig: RestConfig,
		clientConfigProperties: ClientConfigurationProperties,
		oAuth2AccessTokenService: OAuth2AccessTokenService,
		subjectHandler: SubjectHandlerInterface
	) = azureClient(restConfig.azureUrl, clientConfigProperties.registration["azure"]!!, oAuth2AccessTokenService, subjectHandler)

	private fun azureClient(
		baseUrl: String,
		clientProperties: ClientProperties,
		oAuth2AccessTokenService: OAuth2AccessTokenService,
		subjectHandler: SubjectHandlerInterface
	): RestClient {

		val tokenService = TokenService(clientProperties, oAuth2AccessTokenService)

		return RestClient.builder()
			.baseUrl(baseUrl)
			.requestFactory(timeouts())
			.requestInterceptor(AddRequestHeaders(tokenService, subjectHandler))
			.build()
	}

	@Bean
	@Profile("!(prod | dev)")
	@Qualifier("azureApiRestClient")
	fun azureApiClientWithoutAuth(restConfig: RestConfig) = RestClient.builder().baseUrl(restConfig.azureUrl).build()

	@Bean
	@Qualifier("kodeverkApiClient")
	fun kodeverkApiClient(restConfig: RestConfig): RestClient {
		val callId = MDCUtil.callIdOrNew()

		return RestClient.builder()
			.baseUrl(restConfig.kodeverkUrl)
			.requestFactory(timeouts())
			.defaultHeaders { headers ->
				headers.set("Nav-Consumer-Id", applicationName)
				headers.set("Nav-Call-Id", callId)
			}
			.build()
	}

	@Bean
	@Profile("prod | dev")
	@Qualifier("kontoregisterApiRestClient")
	fun kontoregisterApiClient(
		restConfig: RestConfig,
		clientConfigProperties: ClientConfigurationProperties,
		oAuth2AccessTokenService: OAuth2AccessTokenService
	) = kontoregisterClient(restConfig.kontoregisterUrl, clientConfigProperties.registration["kontoregister"]!!, oAuth2AccessTokenService)

	private fun kontoregisterClient(
		baseUrl: String,
		clientProperties: ClientProperties,
		oAuth2AccessTokenService: OAuth2AccessTokenService,
	): RestClient {

		val tokenService = TokenService(clientProperties, oAuth2AccessTokenService)

		return RestClient.builder()
			.baseUrl(baseUrl)
			.requestFactory(timeouts())
			.requestInterceptor(AddRequestHeaders(tokenService))
			.build()
	}

	@Bean
	@Profile("!(prod | dev)")
	@Qualifier("kontoregisterApiRestClient")
	fun kontoregisterApiClientWithoutAuth(restConfig: RestConfig) = RestClient.builder().baseUrl(restConfig.kontoregisterUrl+"/api/borger").build()


	@Bean
	@Profile("prod | dev")
	@Qualifier("soknadsmottakerRestClient")
	fun soknadsmottakerRestClient(
		restConfig: RestConfig,
		clientConfigProperties: ClientConfigurationProperties,
		oAuth2AccessTokenService: OAuth2AccessTokenService
	) = restClientOAuth2Client(restConfig, clientConfigProperties.registration["soknadsmottaker"]!!, oAuth2AccessTokenService)

	@Bean
	@Profile("!(prod | dev)")
	@Qualifier("soknadsmottakerRestClient")
	fun soknadsmottakerClientWithoutOAuth(restConfig: RestConfig) = RestClient.builder().baseUrl(restConfig.soknadsMottakerHost).build()

	private fun timeouts(): ReactorNettyClientRequestFactory {
		val factory = ReactorNettyClientRequestFactory()
		factory.setReadTimeout(Duration.ofMinutes(1))
		factory.setConnectTimeout(Duration.ofSeconds(20L))
		return factory
	}

	@Bean
	@Qualifier("skjemaRestClient")
	fun skjemaClientWithoutOAuth(restConfig: RestConfig) = RestClient.builder().baseUrl(restConfig.sanityHost).build()

	private fun restClientOAuth2Client(
		restConfig: RestConfig,
		clientProperties: ClientProperties,
		oAuth2AccessTokenService: OAuth2AccessTokenService,
	): RestClient {

		val tokenService = TokenService(clientProperties, oAuth2AccessTokenService)

		return RestClient.builder()
			.baseUrl(restConfig.soknadsMottakerHost) // Sett inn base URL fra ClientProperties
			.requestFactory(timeouts())
			.defaultHeaders { headers ->
				headers.set("x-innsendingsId", MDC.get(Constants.MDC_INNSENDINGS_ID) ?: "")
				headers.setBearerAuth(tokenService.getToken() ?:"") // Bruker bearerAuth for OAuth2 token
			}
			.build()
	}


	class AddRequestHeaders(val tokenService: TokenService, val subjectHandler: SubjectHandlerInterface? = null) : ClientHttpRequestInterceptor {

		val logger: Logger = LoggerFactory.getLogger(javaClass)

		override fun intercept(
			request: HttpRequest,
			body: ByteArray,
			execution: ClientHttpRequestExecution
		): ClientHttpResponse {
			val token = tokenService.getToken()
			val callId = MDCUtil.callIdOrNew()

			logger.info("Kaller arena med callId: $callId")

			request.headers.setBearerAuth(token ?:"")
			request.headers.set(Constants.HEADER_CALL_ID, callId)

			if (subjectHandler?.getUserIdFromToken() != null) {
				request.headers.set(Constants.NAV_PERSON_IDENT, subjectHandler.getUserIdFromToken() )
			}

			return execution.execute(request, body)
		}
	}


}