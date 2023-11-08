package no.nav.soknad.innsending.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.soknad.innsending.exceptions.BackendErrorException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.context.annotation.Scope
import org.springframework.http.HttpHeaders
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.web.reactive.function.client.*
import reactor.netty.Connection
import reactor.netty.http.client.HttpClient
import java.util.concurrent.TimeUnit

@Configuration
@Profile("test | prod | dev")
@EnableConfigurationProperties(RestConfig::class)
class ArenaClientConfig(
	private val clientConfigurationProperties: ClientConfigurationProperties,
	private val oAuth2AccessTokenService: OAuth2AccessTokenService
) {

	private val logger: Logger = LoggerFactory.getLogger(javaClass)

	@Bean
	@Qualifier("arenaClient")
	@Scope("prototype")
	fun arenaClient(): WebClient {
		val clientProperties = clientConfigurationProperties.registration["arena"]
			?: throw BackendErrorException("Fant ikke konfigurering for arena")

		val httpClient = buildHttpClient(5000, 60, 60)
		return webclientBuilder(httpClient, clientProperties).build()
	}


	fun buildHttpClient(connectionTimeout: Int, readTimeout: Int, writeTimeout: Int): HttpClient {
		return HttpClient.create()
			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeout)
			.doOnConnected { conn: Connection ->
				conn
					.addHandler(ReadTimeoutHandler(readTimeout.toLong(), TimeUnit.SECONDS))
					.addHandler(WriteTimeoutHandler(writeTimeout))
			}
	}

	fun webclientBuilder(httpClient: HttpClient, clientProperties: ClientProperties): WebClient.Builder {
		return WebClient.builder()
			.exchangeStrategies(createExchangeStrategies())
			.clientConnector(ReactorClientHttpConnector(httpClient))
			.filter(bearerTokenExchange(clientProperties))
	}

	private fun createExchangeStrategies(): ExchangeStrategies {
		return ExchangeStrategies.builder()
			.codecs { configurer: ClientCodecConfigurer -> configurer.defaultCodecs() }
			.build()
	}

	private fun bearerTokenExchange(clientProperties: ClientProperties): ExchangeFilterFunction {
		return ExchangeFilterFunction { clientRequest: ClientRequest?, exchangeFunction: ExchangeFunction ->
			val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
			logger.info("Bearer token: ${response.accessToken}")
			
			val filtered = ClientRequest.from(clientRequest!!)
				.headers { headers: HttpHeaders -> headers.setBearerAuth(response.accessToken) }
				.build()
			exchangeFunction.exchange(filtered)
		}
	}


}
