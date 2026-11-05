package no.nav.soknad.innsending.config

import no.nav.soknad.innsending.consumerapis.texas.TexasTokenClient
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
	fun texasTokenClient(
		@Value("\${texas.token-endpoint}") tokenEndpoint: String,
		@Value("\${texas.exchange-endpoint}") exchangeEndpoint: String,
	): TexasTokenClient = TexasTokenClient(restClient(TOKEN_TIMEOUT), tokenEndpoint, exchangeEndpoint)

	private fun restClient(timeout: Duration): RestClient {
		val connectTimeout = timeout.dividedBy(4)
		val requestFactory = SimpleClientHttpRequestFactory().apply {
			setConnectTimeout(connectTimeout)
			setReadTimeout(timeout.minus(connectTimeout))
		}
		return RestClient.builder().requestFactory(requestFactory).build()
	}

	private companion object {
		// Vedtatt i #461: 5 sekunder for tokenkall, uten retry i appen. Introspeksjon ligger i TexasIntrospectionConfig
		val TOKEN_TIMEOUT: Duration = Duration.ofSeconds(5)
	}
}
