package no.nav.soknad.pdfutilities.gotenberg

import no.nav.soknad.innsending.embedded.Gotenberg
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

@Configuration
class GotenbergClientConfig {

	@Bean
	@Profile("prod | dev | loadtests | docker")
	@Qualifier("gotenbergClient")
	fun getGotenbergClient(@Value("\${fil-konvertering_url}") baseUrl: String): RestClient {
		return RestClient
			.builder()
			.baseUrl(baseUrl)
			.defaultHeaders({
				it.contentType = MediaType.MULTIPART_FORM_DATA
				it.accept = listOf(MediaType.APPLICATION_PDF, MediaType.TEXT_PLAIN)
			})
			.build()
	}

	@Bean
	@Profile("!(prod | dev | loadtests | docker)")
	@Qualifier("gotenbergClient")
	fun getGotenbergClientEmbedded(gotenbergContainer: Gotenberg.Container): RestClient {
		return RestClient
			.builder()
			.baseUrl(gotenbergContainer.getUrl())
			.defaultHeaders({
				it.contentType = MediaType.MULTIPART_FORM_DATA
				it.accept = listOf(MediaType.APPLICATION_PDF, MediaType.TEXT_PLAIN)
			})
			.build()
	}
}
