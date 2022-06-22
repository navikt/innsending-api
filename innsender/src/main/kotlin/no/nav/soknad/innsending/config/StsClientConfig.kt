package no.nav.soknad.innsending.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClient
import java.util.*

@Configuration
class StsClientConfig(
	private val webClientBuilder: WebClient.Builder,
	private val restConfig: RestConfig) {

	@Bean
	fun stsWebClient(): WebClient {
		return webClientBuilder
			.baseUrl("${restConfig.stsUrl}/rest/v1/sts/token")
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic ${credentials()}")
			.defaultHeader("Nav-Consumer-Id", restConfig.username)
			.build()
	}

	private fun credentials() =
		Base64.getEncoder().encodeToString("${restConfig.username}:${restConfig.password}".toByteArray(Charsets.UTF_8))
}
