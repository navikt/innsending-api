package no.nav.soknad.innsending.config

import com.expediagroup.graphql.client.spring.GraphQLWebClient
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.MDCUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.http.client.HttpClientRequest
import reactor.netty.http.client.HttpClientResponse

@Configuration
@Profile("test | prod | dev")
@EnableConfigurationProperties(RestConfig::class)
class SafClientConfig(
	private val restConfig: RestConfig,
	oauth2Config: ClientConfigurationProperties,
	private val oAuth2AccessTokenService: OAuth2AccessTokenService,
	@Value("\${spring.application.name}") private val applicationName: String
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	@Bean("safGraphQLWebClient")
	fun safGraphQLWebClient() = GraphQLWebClient(
		url = "${restConfig.safUrl}/graphql",
		builder = WebClient.builder()
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
				it.header(Constants.CORRELATION_ID, MDCUtil.callIdOrNew())
				it.header(Constants.NAV_CONSUMER_ID, applicationName)
				it.header(
					HttpHeaders.AUTHORIZATION,
					"Bearer ${oAuth2AccessTokenService.getAccessToken(clientProperties).access_token}",
				)
			}
	)

	private val safMaskintilmaskin = "saf-maskintilmaskin"

	private val clientProperties = oauth2Config.registration[safMaskintilmaskin]
		?: throw RuntimeException("could not find oauth2 client config for $safMaskintilmaskin")
}
