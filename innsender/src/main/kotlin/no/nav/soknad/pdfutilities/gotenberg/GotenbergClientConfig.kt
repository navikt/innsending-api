package no.nav.soknad.pdfutilities.gotenberg

import no.nav.soknad.innsending.embedded.Gotenberg
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class GotenbergClientConfig {

	private val defaultReadTimeout = Duration.ofSeconds(180)
	private val defaultConnectTimeout = Duration.ofSeconds(60)


	@Bean
	@Profile("prod | dev | loadtests | endtoend | docker")
	@Qualifier("gotenbergClient")
	fun getGotenbergClient(@Value("\${fil-konvertering_url}") baseUrl: String): RestClient {
		return RestClient
			.builder()
			.baseUrl(baseUrl)
			.requestFactory(timeouts())
			.defaultHeaders({
				it.contentType = MediaType.MULTIPART_FORM_DATA
				it.accept = listOf(MediaType.APPLICATION_PDF, MediaType.TEXT_PLAIN)
			})
			.build()
	}

	@Bean
	@Profile("!(prod | dev | loadtests | endtoend | docker)")
	@Qualifier("gotenbergClient")
	fun getGotenbergClientEmbedded(gotenbergContainer: Gotenberg.Container): RestClient {
		return RestClient
			.builder()
			.baseUrl(gotenbergContainer.getUrl())
			.requestFactory(timeouts())
			.defaultHeaders({
				it.contentType = MediaType.MULTIPART_FORM_DATA
				it.accept = listOf(MediaType.APPLICATION_PDF, MediaType.TEXT_PLAIN)
			})
			.build()
	}

	private fun timeouts(): ClientHttpRequestFactory {
		val factory =	SimpleClientHttpRequestFactory()
		factory.setReadTimeout(defaultReadTimeout)
		factory.setConnectTimeout(defaultConnectTimeout)
		return factory
	}

}
