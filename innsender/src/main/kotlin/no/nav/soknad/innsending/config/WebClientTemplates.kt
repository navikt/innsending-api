package no.nav.soknad.innsending.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Profile
import org.springframework.context.annotation.Scope
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.Connection
import reactor.netty.http.client.HttpClient
import java.util.concurrent.TimeUnit

@Configuration
@EnableConfigurationProperties(RestConfig::class)
class WebClientTemplates(private val restConfig: RestConfig) {

	private val connectionTimeout = 2000
	private val readTimeout = 60
	private val writeTimeout = 60


	@Bean
	@Profile("spring | docker | default")
	@Qualifier("authClient")
	@Scope("prototype")
	@Lazy
	fun archiveTestWebClient() = WebClient.builder().defaultHeader("testHeader","test_value").build()

	@Bean
	@Profile("test | prod | dev")
	@Qualifier("authClient")
	@Scope("prototype")
	@Lazy
	fun authWebClient() = WebClient.builder().defaultHeader("authHeader","test_value").build()


	@Bean
	@Qualifier("basicClient")
	@Scope("prototype")
	fun basicWebClientTemplate(): WebClient {
		val exchangeStrategies = ExchangeStrategies.builder()
			.codecs { configurer: ClientCodecConfigurer ->
				configurer
					.defaultCodecs().maxInMemorySize(restConfig.maxFileSizeSum*1024*1024) }
			.build()

		return WebClient.builder()
			.exchangeStrategies(exchangeStrategies)
			.clientConnector(ReactorClientHttpConnector(buildHttpClient(connectionTimeout, readTimeout, writeTimeout)))
			.build()
	}

	private fun buildHttpClient(connection_timeout: Int, readTimeout: Int, writeTimeout: Int): HttpClient {
		return HttpClient.create()
			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connection_timeout)
			.doOnConnected { conn: Connection ->
				conn
					.addHandlerLast(ReadTimeoutHandler(readTimeout.toLong(), TimeUnit.SECONDS))
					.addHandlerLast(WriteTimeoutHandler(writeTimeout))
			}
	}

}
