package no.nav.soknad.innsending.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.Connection
import reactor.netty.http.client.HttpClient
import java.util.concurrent.TimeUnit

@Configuration
class WebClientTemplates(private val appConfiguration: AppConfiguration) {

	private val CONNECTION_TIMEOUT = 2000
	private val READ_TIMEOUT = 60
	private val WRITE_TIMEOUT = 60

	@Bean
	@Qualifier("basicClient")
	@Scope("prototype")
	fun basicWebClientTemplate(
		restTemplateBuilder: RestTemplateBuilder
	): WebClient {
		val exchangeStrategies = ExchangeStrategies.builder()
			.codecs { configurer: ClientCodecConfigurer ->
				configurer
					.defaultCodecs()
					.maxInMemorySize(appConfiguration.restConfig.maxFileSize) }
			.build()

		return WebClient.builder()
			.exchangeStrategies(exchangeStrategies)
			.clientConnector(ReactorClientHttpConnector(buildHttpClient(CONNECTION_TIMEOUT, READ_TIMEOUT, WRITE_TIMEOUT)))
			.build()
	}

	private fun buildHttpClient(connection_timeout: Int, readTimeout: Int, writeTimeout: Int): HttpClient {
		return HttpClient.create()
			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connection_timeout)
			.doOnConnected { conn: Connection ->
				conn
					.addHandler(ReadTimeoutHandler(readTimeout.toLong(), TimeUnit.SECONDS))
					.addHandler(WriteTimeoutHandler(writeTimeout))
			}
	}

}
