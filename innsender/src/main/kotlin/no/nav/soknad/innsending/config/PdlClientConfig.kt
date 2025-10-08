package no.nav.soknad.innsending.config

import com.expediagroup.graphql.client.spring.GraphQLWebClient
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutException
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.soknad.innsending.exceptions.utils.messageForLog
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.Constants.HEADER_BEHANDLINGSNUMMER
import no.nav.soknad.innsending.util.Constants.PDL_BEHANDLINGSNUMMER
import no.nav.soknad.innsending.util.MDCUtil
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.*
import org.springframework.http.HttpHeaders
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import reactor.netty.http.client.*
import reactor.util.retry.Retry
import java.util.concurrent.TimeUnit


@Configuration
@Profile("test | prod | dev")
@EnableConfigurationProperties(RestConfig::class)
class PdlClientConfig(
	private val restConfig: RestConfig,
	oauth2Config: ClientConfigurationProperties,
	private val oAuth2AccessTokenService: OAuth2AccessTokenService,
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	private val connectionTimeoutSeconds = 10
	private val readTimeoutSeconds = 15
	private val writeTimeoutSeconds = 30
	private val maxRetries = 3L

	@Bean("pdlGraphQLClient")
	fun graphQLClient() = GraphQLWebClient(
		url = "${restConfig.pdlUrl}/graphql",
		builder = WebClient.builder()
			.clientConnector(
				ReactorClientHttpConnector(
					HttpClient.create()
						.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (connectionTimeoutSeconds * 1000))
						.doOnConnected { conn ->
							conn.addHandlerLast(ReadTimeoutHandler(readTimeoutSeconds.toLong(), TimeUnit.SECONDS))
							conn.addHandlerLast(WriteTimeoutHandler(writeTimeoutSeconds.toLong(), TimeUnit.SECONDS))
						}
						.doOnRequest { request: HttpClientRequest, _ ->
							logger.info("OnRequest: {} {} {}", request.version(), request.method(), request.resourceUrl())
						}
						.doOnResponse { response: HttpClientResponse, _ ->
							logger.info(
								"OnResponse: {} - {} {} {}",
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
				it.header(HttpHeaders.AUTHORIZATION, "Bearer ${oAuth2AccessTokenService.getAccessToken(tokenxPDLClientProperties).access_token}")
				it.header("Tema", "AAP")
				it.header(HEADER_BEHANDLINGSNUMMER, PDL_BEHANDLINGSNUMMER)
			}
			.filter { request, next ->
				next.exchange(request)
					.retryWhen(
						Retry.max(maxRetries)
							.filter { throwable -> throwable is WebClientRequestException && throwable.cause is ReadTimeoutException }
						.doBeforeRetry { logger.info("Retrying due to read timeout (attempt ${it.totalRetries() + 1}/${maxRetries}), error: ${it.failure().messageForLog}") }
					).doOnError { error -> logger.error("Error in call to PDL - ${error.messageForLog}", error) }
			}
	)

	private val tokenxPDLClientProperties =
		oauth2Config.registration["tokenx-pdl"]
			?: throw RuntimeException("could not find oauth2 client config for tokenx-pdl")

}
