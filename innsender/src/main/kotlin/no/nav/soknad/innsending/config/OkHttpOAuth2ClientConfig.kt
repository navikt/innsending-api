package no.nav.soknad.innsending.config

import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.soknad.arkivering.soknadsarkiverer.service.tokensupport.TokenService
import no.nav.soknad.innsending.util.Constants.MDC_INNSENDINGS_ID
import okhttp3.OkHttpClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.util.concurrent.TimeUnit

@Configuration
class OkHttpOAuth2ClientConfig {
	val logger: Logger = LoggerFactory.getLogger(javaClass)

	@Bean
	@Profile("prod | dev")
	@Qualifier("soknadsmottakerClient")
	fun soknadsmottakerClient(
		clientConfigProperties: ClientConfigurationProperties,
		oAuth2AccessTokenService: OAuth2AccessTokenService
	) = okHttpOAuth2Client(clientConfigProperties.registration["soknadsmottaker"]!!, oAuth2AccessTokenService)

	private fun okHttpOAuth2Client(
		clientProperties: ClientProperties,
		oAuth2AccessTokenService: OAuth2AccessTokenService,
	): OkHttpClient {

		val tokenService = TokenService(clientProperties, oAuth2AccessTokenService)

		return OkHttpClient().newBuilder()
			.connectTimeout(20, TimeUnit.SECONDS)
			.callTimeout(62, TimeUnit.SECONDS)
			.readTimeout(1, TimeUnit.MINUTES)
			.writeTimeout(1, TimeUnit.MINUTES)
			.addInterceptor {
				val token = tokenService.getToken()
				val innsendingsId = MDC.get(MDC_INNSENDINGS_ID)

				val bearerRequest = it.request().newBuilder().headers(it.request().headers)
					.header("x-innsendingsId", innsendingsId ?: "")
					.header("Authorization", "Bearer $token").build()

				it.proceed(bearerRequest)
			}.build()
	}

	@Bean
	@Profile("!(prod | dev)")
	@Qualifier("soknadsmottakerClient")
	fun soknadsmottakerClientWithoutOAuth() = OkHttpClient.Builder().build()
}
