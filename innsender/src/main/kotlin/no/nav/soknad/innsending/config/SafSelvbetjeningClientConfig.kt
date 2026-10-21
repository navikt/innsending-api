package no.nav.soknad.innsending.config

import com.expediagroup.graphql.client.spring.GraphQLWebClient
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.Constants.CORRELATION_ID
import no.nav.soknad.innsending.util.Constants.NAV_CONSUMER_ID
import no.nav.soknad.innsending.util.MDCUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.SignalType
import reactor.netty.http.client.HttpClient
import reactor.netty.http.client.HttpClientRequest
import reactor.netty.http.client.HttpClientResponse
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@Configuration
@Profile("test | prod | dev")
@EnableConfigurationProperties(RestConfig::class)
class SafSelvbetjeningClientConfig(
	private val restConfig: RestConfig,
	oauth2Config: ClientConfigurationProperties,
	private val oAuth2AccessTokenService: OAuth2AccessTokenService,
	@Value("\${spring.application.name}") private val applicationName: String
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	@Bean(
		"safSelvbetjeningGraphQLClient"
	)
//	@Scope("prototype")
//	@Lazy
	fun graphQLClient() = GraphQLWebClient(
		url = "${restConfig.safselvbetjeningUrl}/graphql",
		builder = WebClient.builder()
			.codecs { it.defaultCodecs().maxInMemorySize(maxResponseSizeBytes()) }
			.filter { request, next -> next.exchange(request).map(::logResponseSize) }
			.clientConnector(
				ReactorClientHttpConnector(
					HttpClient.create()
						.doOnRequest { request: HttpClientRequest, _ ->
							logger.info("{} {} {}", request.version(), request.method(), request.resourceUrl())
						}
						.doOnResponse { response: HttpClientResponse, _ ->
							logger.info(
								"{} - {} {} {}",
								response.status().toString(),
								response.version(),
								response.method(),
								response.resourceUrl()
							)
						}
				)
			)
			.defaultRequest {
				it.header(Constants.HEADER_CALL_ID, MDCUtil.callIdOrNew())
				it.header(CORRELATION_ID, MDCUtil.callIdOrNew())
				it.header(NAV_CONSUMER_ID, applicationName)
				it.header(
					HttpHeaders.AUTHORIZATION,
					"Bearer ${oAuth2AccessTokenService.getAccessToken(tokenxSafSelvbetjeningClientProperties).access_token}",
				)
			}
	)

	private fun maxResponseSizeBytes(): Int {
		val bytes = restConfig.safMaxInMemorySize.toBytes()
		require(bytes in 1..Int.MAX_VALUE.toLong()) {
			"restconfig.safMaxInMemorySize must be between 1 and ${Int.MAX_VALUE} bytes"
		}
		return bytes.toInt()
	}

	private fun logResponseSize(response: ClientResponse): ClientResponse {
		val receivedBytes = AtomicLong()
		val logged = AtomicBoolean()
		fun log(signal: SignalType) {
			if (logged.compareAndSet(false, true)) {
				logger.atInfo()
					.addKeyValue("saf_response_bytes_received", receivedBytes.get())
					.addKeyValue("saf_response_body_terminal_signal", signal.toString())
					.log("SAF response body received")
			}
		}
		return response.mutate()
			.body { body ->
				body.doOnNext { receivedBytes.addAndGet(it.readableByteCount().toLong()) }
					// Log before propagating the signal: error handling may subscribe again to release the body.
					.doOnComplete { log(SignalType.ON_COMPLETE) }
					.doOnError { log(SignalType.ON_ERROR) }
					.doOnCancel { log(SignalType.CANCEL) }
			}
			.build()
	}

	private val tokenxSafSelvbetjeningClientProperties =
		oauth2Config.registration["tokenx-safselvbetjening"]
			?: throw RuntimeException("could not find oauth2 client config for tokenx-safselvbetjening")

}
