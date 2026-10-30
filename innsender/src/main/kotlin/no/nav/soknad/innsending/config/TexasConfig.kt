package no.nav.soknad.innsending.config

import no.nav.soknad.innsending.consumerapis.texas.TexasIntrospeksjonKlient
import no.nav.soknad.innsending.consumerapis.texas.TexasTokenKlient
import no.nav.soknad.innsending.security.TexasSkygge
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
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
	): TexasTokenKlient = TexasTokenKlient(restClient(TOKEN_TIMEOUT), tokenEndpoint, exchangeEndpoint)

	@Bean
	fun texasIntrospeksjonKlient(
		@Value("\${texas.introspection-endpoint}") introspectionEndpoint: String,
	): TexasIntrospeksjonKlient = TexasIntrospeksjonKlient(restClient(INTROSPEKSJON_TIMEOUT), introspectionEndpoint)

	// Skyggemodus i steg 3h. Utviklerteamet avgjør hvor lenge den kjører før 3i og 3j
	@Bean
	@ConditionalOnProperty(name = ["texas.skygge.aktiv"], havingValue = "true")
	fun texasSkygge(
		klient: TexasIntrospeksjonKlient,
		metrics: InnsenderMetrics,
		@Value("\${texas.skygge.andel:1.0}") andel: Double,
	): TexasSkygge = TexasSkygge(klient, metrics, andel.coerceIn(0.0, 1.0))

	private fun restClient(timeout: Duration): RestClient {
		val requestFactory = SimpleClientHttpRequestFactory().apply {
			setConnectTimeout(timeout)
			setReadTimeout(timeout)
		}
		return RestClient.builder().requestFactory(requestFactory).build()
	}

	private companion object {
		// Vedtatt i #461: 5 sekunder for tokenkall og 2 sekunder for introspeksjon, uten retry i appen
		val TOKEN_TIMEOUT: Duration = Duration.ofSeconds(5)
		val INTROSPEKSJON_TIMEOUT: Duration = Duration.ofSeconds(2)
	}
}
