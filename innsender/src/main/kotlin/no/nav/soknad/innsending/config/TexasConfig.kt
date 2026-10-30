package no.nav.soknad.innsending.config

import no.nav.soknad.innsending.consumerapis.texas.TexasTokenKlient
import no.nav.soknad.innsending.consumerapis.texas.TokenIntrospeksjon
import no.nav.soknad.innsending.security.TexasSkygge
import no.nav.soknad.innsending.security.TokenUtsteder
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

	// Skyggemodus i steg 3h. Utviklerteamet avgjør hvor lenge den kjører før 3i og 3j.
	// Sammenligner bare utstederne token-support fortsatt validerer, og slås av når Texas har tatt over alle
	@Bean
	@ConditionalOnProperty(name = ["texas.skygge.aktiv"], havingValue = "true")
	fun texasSkygge(
		introspeksjon: TokenIntrospeksjon,
		metrics: InnsenderMetrics,
		@Value("\${texas.skygge.andel:1.0}") andel: Double,
		@Value("\${texas.innkommende.utstedere:}") texasUtstedere: String,
	): TexasSkygge? {
		val viaTokenSupport = TokenUtsteder.entries.toSet() - TokenUtsteder.fraListe(texasUtstedere)
		if (viaTokenSupport.isEmpty()) return null
		return TexasSkygge(introspeksjon, metrics, andel.coerceIn(0.0, 1.0), utstedere = viaTokenSupport)
	}

	private fun restClient(timeout: Duration): RestClient {
		val requestFactory = SimpleClientHttpRequestFactory().apply {
			setConnectTimeout(timeout)
			setReadTimeout(timeout)
		}
		return RestClient.builder().requestFactory(requestFactory).build()
	}

	private companion object {
		// Vedtatt i #461: 5 sekunder for tokenkall, uten retry i appen. Introspeksjon ligger i TexasIntrospeksjonConfig
		val TOKEN_TIMEOUT: Duration = Duration.ofSeconds(5)
	}
}
