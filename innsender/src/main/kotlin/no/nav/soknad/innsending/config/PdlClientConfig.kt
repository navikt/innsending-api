package no.nav.soknad.innsending.config

import com.expediagroup.graphql.client.spring.GraphQLWebClient
import no.nav.soknad.innsending.consumerapis.azure.AzureInterface
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
import reactor.netty.http.client.*


@Configuration
@Profile("test | prod | dev")
@EnableConfigurationProperties(RestConfig::class)
class PdlClientConfig(
	private val restConfig: RestConfig,
	private val azureClient: AzureInterface
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
				it.header(HttpHeaders.AUTHORIZATION, azureClient.consumerToken())
				it.header("Tema", "AAP")
				it.header(HEADER_BEHANDLINGSNUMMER, PDL_BEHANDLINGSNUMMER)
			}
	)


}

