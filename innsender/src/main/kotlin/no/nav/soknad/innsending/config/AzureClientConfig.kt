package no.nav.soknad.innsending.config

import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.MDCUtil
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Configuration
class AzureClientConfig(
	private val webClientBuilder: WebClient.Builder,
	private val restConfig: RestConfig,
	private val azureAdV2Cache: AzureAdV2Cache
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	@Bean
	fun azureWebClient(): WebClient {
		return webClientBuilder
			.baseUrl("${restConfig.azureUrl}")
			.defaultRequest{
				it.header(Constants.HEADER_CALL_ID, MDCUtil.callIdOrNew())
			}
			.build()
	}

	fun consumerToken(): String? {
		val token = azureAdV2Cache.getToken(restConfig.pdlScope)?.accessToken
			?: getServiceUserAccessToken()?.let {
				azureAdV2Cache.putValue(restConfig.pdlScope, it.toAzureAdV2Token())
			}?.accessToken

		return Constants.BEARER + token
	}

	private fun getServiceUserAccessToken(): AzureADV2TokenResponse? {
		val map: MultiValueMap<String, String> = LinkedMultiValueMap()
		map.add("client_id",restConfig.clientId)
		map.add("client_secret",restConfig.clientSecret)
		map.add("scope",restConfig.pdlScope)
		map.add("grant_type","client_credentials")

		try {
			return azureWebClient().post()
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.accept(MediaType.APPLICATION_JSON)
				.body(BodyInserters.fromFormData(map))
				.retrieve()
				.bodyToMono<AzureADV2TokenResponse>()
				.block()
		} catch (ex: Exception) {
			logger.error("Henting av token på url ${restConfig.azureUrl} for client ${restConfig.clientId} feilet med:\n${ex.message}")
			throw ex
		}
	}

}
