package no.nav.soknad.innsending.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Profile
import org.springframework.context.annotation.Scope
import org.springframework.http.client.ReactorNettyClientRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
@EnableConfigurationProperties(RestConfig::class)
class RestClientTemplates {
	private val connectionTimeout = 2L
	private val readTimeout = 1L
	private val writeTimeout = 60

	@Bean
	@Profile("local | docker")
	@Qualifier("authRestClient")
	@Scope("prototype")
	@Lazy
	fun archiveTestRestClient(): RestClient = RestClient.builder().defaultHeader("testHeader", "test_value").build()

	@Bean
	@Profile("test | prod | dev")
	@Qualifier("authRestClient")
	@Scope("prototype")
	@Lazy
	fun authRestClient(): RestClient = RestClient.builder().defaultHeader("authHeader", "test_value").build()


	@Bean
	@Qualifier("basicRestClient")
	@Scope("prototype")
	fun basicRestClientTemplate(): RestClient {

		return RestClient.builder()
			.requestFactory(timeouts())
			.build()
	}


	private fun timeouts(): ReactorNettyClientRequestFactory {
		val factory = ReactorNettyClientRequestFactory()
		factory.setReadTimeout(Duration.ofMinutes(readTimeout))
		factory.setConnectTimeout(Duration.ofSeconds(connectionTimeout))
		return factory
	}

}
