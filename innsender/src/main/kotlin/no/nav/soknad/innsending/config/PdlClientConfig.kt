package no.nav.soknad.innsending.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class PdlClientConfig(
	private val webClientBuilder: WebClient.Builder,
	private val restConfig: RestConfig) {

	@Bean
	fun pdlWebClient(): WebClient {
		return webClientBuilder
			.baseUrl(restConfig.pdlUrl)
			.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
			.defaultHeader("Nav-Consumer-Id", restConfig.username)
			.build()
	}
}

