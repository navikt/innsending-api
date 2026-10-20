package no.nav.soknad.innsending.config

import io.github.resilience4j.retry.RetryConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.security.token.support.client.core.OAuth2ClientException
import no.nav.security.token.support.client.core.http.OAuth2HttpClient
import no.nav.security.token.support.client.core.http.OAuth2HttpRequest
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenResponse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.ResourceAccessException
import java.net.URI
import java.time.Duration

class RetryingOAuth2HttpClientTest {

	private val delegate = mockk<OAuth2HttpClient>()
	private val request = OAuth2HttpRequest.builder(URI("https://token.example.test"))
		.formParameter("grant_type", "client_credentials")
		.build()
	private val retryConfig = RetryConfig.custom<OAuth2AccessTokenResponse>()
		.maxAttempts(2)
		.waitDuration(Duration.ZERO)
		.retryExceptions(ResourceAccessException::class.java)
		.build()

	@Test
	fun `retries a transient transport failure`() {
		val expected = OAuth2AccessTokenResponse(access_token = "token")
		every { delegate.post(request) }
			.throws(ResourceAccessException("connection reset"))
			.andThen(expected)

		val response = RetryingOAuth2HttpClient(delegate, retryConfig).post(request)

		assertSame(expected, response)
		verify(exactly = 2) { delegate.post(request) }
	}

	@Test
	fun `stops after the configured number of transport attempts`() {
		every { delegate.post(request) } throws ResourceAccessException("connection reset")

		assertThrows<ResourceAccessException> {
			RetryingOAuth2HttpClient(delegate, retryConfig).post(request)
		}
		verify(exactly = 2) { delegate.post(request) }
	}

	@Test
	fun `does not retry OAuth error responses`() {
		every { delegate.post(request) } throws OAuth2ClientException("401 Unauthorized")

		assertThrows<OAuth2ClientException> {
			RetryingOAuth2HttpClient(delegate, retryConfig).post(request)
		}
		verify(exactly = 1) { delegate.post(request) }
	}
}
