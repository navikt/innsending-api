package no.nav.soknad.innsending.config

import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.MDCUtil
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class AzureClientConfig(
	private val webClientBuilder: WebClient.Builder,
	private val restConfig: RestConfig
) {

	@Bean
	fun azureWebClient(): WebClient {
		return webClientBuilder
			.baseUrl(restConfig.azureUrl)
			.defaultRequest {
				it.header(Constants.HEADER_CALL_ID, MDCUtil.callIdOrNew())
			}
			.build()
	}
}
