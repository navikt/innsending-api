package no.nav.soknad.innsending.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class AntivirusClientConfig(private val webClientBuilder: WebClient.Builder) {

	@Value("\${ANTIVIRUS_URL}")
	private lateinit var url: String

	@Bean
	fun antivirusWebClient(): WebClient {
		return webClientBuilder
			.baseUrl(url)
			.build()
	}
}
