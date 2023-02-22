package no.nav.soknad.innsending.config

import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class SafClientConfig (
	private val webClientBuilder: WebClient.Builder,
	private val restConfig: RestConfig) {

	fun safWebClient(): WebClient {
		return webClientBuilder
			.baseUrl(restConfig.safselvbetjeningUrl)
			.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
			.defaultHeader("Nav-Consumer-Id", restConfig.username)
			.build()
	}
}
