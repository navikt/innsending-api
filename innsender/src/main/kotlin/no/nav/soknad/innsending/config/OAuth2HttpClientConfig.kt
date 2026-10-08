package no.nav.soknad.innsending.config

import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import no.nav.security.token.support.client.core.http.OAuth2HttpClient
import no.nav.security.token.support.client.core.http.OAuth2HttpRequest
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenResponse
import no.nav.security.token.support.client.spring.oauth2.DefaultOAuth2HttpClient
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.web.client.ResourceAccessException
import java.time.Duration

@Configuration
class OAuth2HttpClientConfig {

	@Bean("retryingOAuth2HttpClient")
	@Primary
	fun oAuth2HttpClient(): OAuth2HttpClient = RetryingOAuth2HttpClient(DefaultOAuth2HttpClient())
}

class RetryingOAuth2HttpClient(
	private val delegate: OAuth2HttpClient,
	retryConfig: RetryConfig = defaultRetryConfig
) : OAuth2HttpClient {

	private val retry = Retry.of("oauth2HttpClient", retryConfig).also {
		it.eventPublisher.onRetry { event ->
			logger.warn(
				"Retrying OAuth token request after transport failure (retry attempt {}): {}",
				event.numberOfRetryAttempts,
				event.lastThrowable.message
			)
		}
	}

	override fun post(req: OAuth2HttpRequest): OAuth2AccessTokenResponse =
		retry.executeSupplier { delegate.post(req) }

	companion object {
		private val logger = LoggerFactory.getLogger(RetryingOAuth2HttpClient::class.java)

		private val defaultRetryConfig: RetryConfig = RetryConfig
			.custom<OAuth2AccessTokenResponse>()
			.maxAttempts(2)
			.waitDuration(Duration.ofMillis(200))
			.retryExceptions(ResourceAccessException::class.java)
			.build()
	}
}
