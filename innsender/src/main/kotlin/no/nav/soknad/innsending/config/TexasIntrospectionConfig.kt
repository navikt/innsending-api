package no.nav.soknad.innsending.config

import no.nav.soknad.innsending.consumerapis.texas.TexasIntrospectionClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Introspeksjon av innkommende token i Texas (#461). Egen konfigurasjon fordi e2e med endtoend-auth også bruker den,
 * mens utgående token i TexasConfig bare finnes i test, dev og prod.
 */
@Configuration
@Profile("test | dev | prod | endtoend-auth")
class TexasIntrospectionConfig {

	@Bean
	fun texasIntrospectionClient(
		@Value("\${texas.introspection-endpoint:}") introspectionEndpoint: String,
	): TexasIntrospectionClient {
		// 0,5 s for å koble til og 1,5 s for hver lesing fra socketen, per forsøk. Grensen gjelder ikke hele kallet: et svar
		// som kommer bit for bit, kan i teorien ta lengre tid. Texas kjører på localhost og svarer med lite JSON
		val connectTimeout = TIMEOUT.dividedBy(4)
		val requestFactory = SimpleClientHttpRequestFactory().apply {
			setConnectTimeout(connectTimeout)
			setReadTimeout(TIMEOUT.minus(connectTimeout))
		}
		return TexasIntrospectionClient(RestClient.builder().requestFactory(requestFactory).build(), introspectionEndpoint)
	}

	private companion object {
		// Vedtatt i #461: 2 sekunder for introspeksjon, fordelt på tilkobling og lesing. TexasRetry prøver én gang
		// til, så et kall kan ta rundt 4,2 sekunder før det gir opp
		val TIMEOUT: Duration = Duration.ofSeconds(2)
	}
}
