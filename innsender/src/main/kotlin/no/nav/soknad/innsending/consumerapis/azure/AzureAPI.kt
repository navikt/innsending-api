package no.nav.soknad.innsending.consumerapis.azure

import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.util.Constants
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import no.nav.soknad.innsending.consumerapis.azure.dto.AzureAdV2TokenResponse
import no.nav.soknad.innsending.consumerapis.azure.dto.toAzureAdV2Token

@Service
@Profile("test | dev | prod")
class AzureAPI(
	private val azureWebClient: WebClient,
	private val restConfig: RestConfig,
	private val azureAdV2Cache: AzureAdV2Cache
) : AzureInterface {

	private val logger = LoggerFactory.getLogger(javaClass)

	override fun consumerToken(): String? {
		val token = azureAdV2Cache.getToken(restConfig.pdlScope)?.accessToken
			?: getServiceUserAccessToken()?.let {
				azureAdV2Cache.putValue(restConfig.pdlScope, it.toAzureAdV2Token())
			}?.accessToken

		return Constants.BEARER + token
	}

	private fun getServiceUserAccessToken(): AzureAdV2TokenResponse? {
		val map: MultiValueMap<String, String> = LinkedMultiValueMap()
		map.add("client_id",restConfig.clientId)
		map.add("client_secret",restConfig.clientSecret)
		map.add("scope",restConfig.pdlScope)
		map.add("grant_type","client_credentials")

		try {
			return azureWebClient.post()
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.accept(MediaType.APPLICATION_JSON)
				.body(BodyInserters.fromFormData(map))
				.retrieve()
				.bodyToMono<AzureAdV2TokenResponse>()
				.block()
		} catch (ex: Exception) {
			logger.error("Henting av token på url ${restConfig.azureUrl} for client ${restConfig.clientId} feilet med:\n${ex.message}")
			throw ex
		}
	}

}
