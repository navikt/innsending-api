package no.nav.soknad.innsending.config

import com.expediagroup.graphql.client.spring.GraphQLWebClient
import jdk.jfr.ContentType
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.Constants.BEARER
import no.nav.soknad.innsending.util.Constants.CONSUMER_TOKEN
import no.nav.soknad.innsending.util.MDCUtil
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.*
import org.springframework.http.HttpHeaders
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.util.MultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.netty.http.client.HttpClient
import reactor.netty.http.client.HttpClientRequest
import reactor.netty.http.client.HttpClientResponse

@Configuration
@Profile("test | prod | dev")
@EnableConfigurationProperties(RestConfig::class)
class PdlClientConfig(
	private val restConfig: RestConfig,
//	private val oAuth2AccessTokenService: OAuth2AccessTokenService,
	private val azureWebClient: WebClient,
	oauth2Config: ClientConfigurationProperties
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	@Bean("pdlGraphQLClient")
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
				it.header(Constants.HEADER_CALL_ID, MDCUtil.callIdOrNew())
				it.header(HttpHeaders.AUTHORIZATION, consumerToken())
				it.header("Tema","AAP")
			}
	)

	private val tokenxPdlClientProperties = oauth2Config.registration["tokenx-pdl"]
		?: throw RuntimeException("could not find oauth2 client config for tokenx-pdl")

	private fun consumerToken(): String? {
		val tokenResponse = getServiceUserAccessToken()
		return BEARER + tokenResponse?.access_token
	}

	private fun getServiceUserAccessToken(): AzureADV2TokenResponse? {
		val map: HashMap<String,String> = hashMapOf(
			"client_id" to restConfig.clientId,
			"client_secret" to restConfig.clientSecret,
			"scope" to restConfig.pdlScope,
			"grant_type" to "client_credentials"
		)
		try {
			return azureWebClient.post()
				.body(BodyInserters.fromValue(map))
				.retrieve()
				.bodyToMono<AzureADV2TokenResponse>()
				.block()
		} catch (ex: Exception) {
			logger.error("Henting av token på url ${restConfig.azureUrl} for client ${restConfig.clientId} feilet med:\n${ex.message}")
			throw ex
		}
	}

}

