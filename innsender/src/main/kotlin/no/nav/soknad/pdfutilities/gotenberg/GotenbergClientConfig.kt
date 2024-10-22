package no.nav.soknad.pdfutilities.gotenberg

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

@Configuration
class GotenbergClientConfig(
	@Value("\${fil-konvertering_url}") private val baseUrl: String,
) {

	@Bean
	@Qualifier("gotenbergClient")
	fun getGotenbergClient(): RestClient {
		return RestClient
			.builder()
			.baseUrl(baseUrl)
			.defaultHeaders({
				it.contentType = MediaType.MULTIPART_FORM_DATA
				it.accept = listOf(MediaType.APPLICATION_PDF, MediaType.TEXT_PLAIN)
			})
			.build()
	}
}
