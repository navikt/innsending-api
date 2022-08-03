package no.nav.soknad.innsending.config

import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.MDCUtil
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import java.util.*

@Configuration
class AzureClientConfig(
	private val webClientBuilder: WebClient.Builder,
	private val restConfig: RestConfig) {

	@Bean
	fun azureWebClient(): WebClient {
		return webClientBuilder
			.baseUrl("${restConfig.azureUrl}")
			.defaultRequest{
				it.header(Constants.HEADER_CALL_ID, MDCUtil.callIdOrNew())
				it.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
			}
			.build()
	}

	private fun credentials() =
		Base64.getEncoder().encodeToString("${restConfig.username}:${restConfig.password}".toByteArray(Charsets.UTF_8))
}
