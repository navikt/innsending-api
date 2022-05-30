package no.nav.soknad.innsending.config

import com.expediagroup.graphql.client.spring.GraphQLWebClient
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.MDCUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.*
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.http.client.HttpClientRequest
import reactor.netty.http.client.HttpClientResponse

@Configuration
@Profile("prod | dev")
@EnableConfigurationProperties(RestConfig::class)
class PdlClientConfig(
	private val restConfig: RestConfig,
	private val oAuth2AccessTokenService: OAuth2AccessTokenService,
	oauth2Config: ClientConfigurationProperties
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	@Bean("pdlGraphQLClient")
	@Scope("prototype")
	@Lazy
	fun graphQLClient() = GraphQLWebClient(
		url = "${restConfig.pdlUrl}/graphql",
		builder = WebClient.builder()
			.clientConnector(
				ReactorClientHttpConnector(
					HttpClient.create()
						.doOnRequest { request: HttpClientRequest, _ ->
							logger.info("{} {} {}", request.version(), request.method(), request.resourceUrl())
						}
						.doOnResponse { response: HttpClientResponse, _ ->
							logger.info("{} - {} {} {}", response.status().toString(), response.version(), response.method(), response.resourceUrl())
						}
				)
			)
			.defaultRequest {
				it.header(Constants.HEADER_CALL_ID, MDCUtil.callId())
				it.header(
					HttpHeaders.AUTHORIZATION,
					"Bearer ${oAuth2AccessTokenService.getAccessToken(tokenxSafSelvbetjeningClientProperties).accessToken}"
				)
			}
	)

	private val tokenxSafSelvbetjeningClientProperties = oauth2Config.registration["tokenx-safselvbetjening"]
		?: throw RuntimeException("could not find oauth2 client config for tokenx-safselvbetjening")
}

