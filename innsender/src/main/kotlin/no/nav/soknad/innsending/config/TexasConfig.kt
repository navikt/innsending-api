package no.nav.soknad.innsending.config

import no.nav.soknad.innsending.consumerapis.texas.TexasTokenKlient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
@Profile("test | dev | prod")
class TexasConfig {

	@Bean
	fun texasTokenKlient(
		@Value("\${texas.token-endpoint}") tokenEndpoint: String,
		@Value("\${texas.exchange-endpoint}") exchangeEndpoint: String,
	): TexasTokenKlient {
		val requestFactory = SimpleClientHttpRequestFactory().apply {
			setConnectTimeout(TIMEOUT)
			setReadTimeout(TIMEOUT)
		}
		return TexasTokenKlient(
			RestClient.builder().requestFactory(requestFactory).build(),
			tokenEndpoint,
			exchangeEndpoint,
		)
	}

	private companion object {
		// Vedtatt i #461: 5 sekunder for tokenkall, uten retry i appen
		val TIMEOUT: Duration = Duration.ofSeconds(5)
	}
}
