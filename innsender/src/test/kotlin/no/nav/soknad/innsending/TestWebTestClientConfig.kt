package no.nav.soknad.innsending

import no.nav.soknad.innsending.config.RestConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.web.reactive.server.WebTestClient

@TestConfiguration
class TestWebTestClientConfig(
	@Value("\${server.port}") private val port: Int,
	private val restConfig: RestConfig
) {

	@Bean
	fun webTestClient(): WebTestClient =
		WebTestClient.bindToServer()
			.baseUrl("http://localhost:$port")
			.codecs { it.defaultCodecs().maxInMemorySize(restConfig.maxFileSize * 1024 * 1024) }
			.build()
}
