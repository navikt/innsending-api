package no.nav.soknad.innsending.config

import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.soknad.arkivering.soknadsarkiverer.service.tokensupport.TokenService
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.Constants.NAV_CONSUMER_ID
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

	private val defaultReadTimeout: Long = 1 // minutes
	private val defaultConnectTimeout: Long = 20 // seconds
	private val defaultExchangeTimeout: Long = 2 // minutes

	@Bean
	@Qualifier("antivirusRestClient")
	fun antivirusRestClient(restConfig: RestConfig): RestClient {
		return RestClient.builder()
			.baseUrl(restConfig.antivirusUrl)
			.requestFactory(timeouts())
			.build()
	}

	@Bean
	@Profile("prod | dev")
	@Qualifier("arenaApiRestClient")
	fun arenaApiClient(
		restConfig: RestConfig,
		clientConfigProperties: ClientConfigurationProperties,
		oAuth2AccessTokenService: OAuth2AccessTokenService,
		subjectHandler: SubjectHandlerInterface
	) = restClientOAuth2Client(
		restConfig.arenaUrl,
		clientConfigProperties.registration["arena"]!!,
		oAuth2AccessTokenService,
		subjectHandler
	)

	@Bean
	@Profile("!(prod | dev)")
	@Qualifier("arenaApiRestClient")
	fun arenaApiClientWithoutAuth(restConfig: RestConfig) = RestClient.builder().baseUrl(restConfig.arenaUrl).build()


	@Bean
	@Profile("prod | dev")
	@Qualifier("kodeverkApiClient")
	fun kodeverkApiClient(
		restConfig: RestConfig,
		clientConfigProperties: ClientConfigurationProperties,
		oAuth2AccessTokenService: OAuth2AccessTokenService,
		subjectHandler: SubjectHandlerInterface
	): RestClient {
		return restClientOAuth2Client(
			restConfig.kodeverkUrl,
			clientConfigProperties.registration["kodeverk"]!!,
			oAuth2AccessTokenService,
			subjectHandler
		)
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
		restConfig: RestConfig,
		clientConfigProperties: ClientConfigurationProperties,
		oAuth2AccessTokenService: OAuth2AccessTokenService
	) = restClientOAuth2Client(
		restConfig.kontoregisterUrl + "/api/borger",
		clientConfigProperties.registration["kontoregister"]!!,
		oAuth2AccessTokenService
	)

	@Bean
	@Profile("!(prod | dev)")
	@Qualifier("kontoregisterApiRestClient")
	fun kontoregisterApiClientWithoutAuth(restConfig: RestConfig) =
		RestClient.builder().baseUrl(restConfig.kontoregisterUrl + "/api/borger").build()


	@Bean
	@Profile("prod | dev | loadtests")
	@Qualifier("soknadsmottakerRestClient")
	fun soknadsmottakerRestClient(
		restConfig: RestConfig,
		clientConfigProperties: ClientConfigurationProperties,
		oAuth2AccessTokenService: OAuth2AccessTokenService
	) = restClientOAuth2Client(
		restConfig.soknadsMottakerHost,
		clientConfigProperties.registration["soknadsmottaker"]!!,
		oAuth2AccessTokenService
	)

	@Bean
	@Profile("!(prod | dev | loadtests)")
	@Qualifier("soknadsmottakerRestClient")
	fun soknadsmottakerClientWithoutOAuth(restConfig: RestConfig) =
		RestClient.builder().baseUrl(restConfig.soknadsMottakerHost).build()

	@Bean
	@Qualifier("skjemaRestClient")
	fun skjemaClientWithoutOAuth(restConfig: RestConfig) = RestClient.builder().baseUrl(restConfig.sanityHost).build()

	private fun timeouts(): ClientHttpRequestFactory {
		val factory =
			SimpleClientHttpRequestFactory()  // MERK: støtter ikke http.patch bruk eventuelt JdkClientHttpRequestFactory
		factory.setReadTimeout(Duration.ofMinutes(defaultReadTimeout))
		factory.setConnectTimeout(Duration.ofSeconds(defaultConnectTimeout))
		//factory.setExchangeTimeout(Duration.ofMinutes(1))
		return factory
	}

	private fun restClientOAuth2Client(
		baseUrl: String,
		clientProperties: ClientProperties,
		oAuth2AccessTokenService: OAuth2AccessTokenService,
		subjectHandler: SubjectHandlerInterface? = null
	): RestClient {

		val tokenService = TokenService(clientProperties, oAuth2AccessTokenService)

		return RestClient.builder()
			.baseUrl(baseUrl)
			.requestFactory(timeouts())
			.requestInterceptor(RequestHeaderInterceptor(tokenService, applicationName, subjectHandler))
			.build()
	}

	class RequestHeaderInterceptor(
		val tokenService: TokenService,
		val applicationName: String,
		val subjectHandler: SubjectHandlerInterface? = null
	) :
		ClientHttpRequestInterceptor {

		val logger: Logger = LoggerFactory.getLogger(javaClass)

		override fun intercept(
			request: HttpRequest,
			body: ByteArray,
			execution: ClientHttpRequestExecution
		): ClientHttpResponse {
			val token = tokenService.getToken()
			val callId = MDCUtil.callIdOrNew()

			logger.info("Kaller service med callId: $callId")

			request.headers.setBearerAuth(token ?: "")
			request.headers.set(Constants.HEADER_CALL_ID, callId)
			request.headers.set(NAV_CONSUMER_ID, applicationName)
			request.headers.set(Constants.HEADER_INNSENDINGSID, MDC.get(Constants.MDC_INNSENDINGS_ID) ?: "")

			try {
				if (subjectHandler?.getUserIdFromToken() != null) {
					request.headers.set(Constants.NAV_PERSON_IDENT, subjectHandler.getUserIdFromToken())
				}
			} catch (ex: Exception) {
				logger.info("Ingen user funnet i token for callId $callId: $ex")
			}

			return execution.execute(request, body)
		}
	}


}
