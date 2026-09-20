package no.nav.soknad.innsending.config

import com.expediagroup.graphql.client.spring.GraphQLWebClient
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.MDCUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.core.OAuth2AuthorizationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.http.client.HttpClientRequest
import reactor.netty.http.client.HttpClientResponse

@Configuration
@Profile("test | prod | dev")
@EnableConfigurationProperties(RestConfig::class)
class SafClientConfig(
	private val restConfig: RestConfig,
	private val accessToken: AccessToken,
	@Value("\${spring.application.name}") private val applicationName: String
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	// Registration-id fra spring.security.oauth2.client.registration.saf-maskintilmaskin (application.yml)
	private val safMaskintilmaskin = "saf-maskintilmaskin"

	// Maskin-til-maskin (client_credentials) - trenger ingen innlogget bruker som principal
	private val m2mPrincipalName = "saf-maskintilmaskin-m2m"

	@Bean("safGraphQLWebClient")
	fun safGraphQLWebClient() = GraphQLWebClient(
		url = "${restConfig.safUrl}/graphql",
		builder = WebClient.builder()
			.clientConnector(
				ReactorClientHttpConnector(
					HttpClient.create()
						.doOnRequest { request: HttpClientRequest, _ ->
							logger.info("{} {} {}", request.version(), request.method(), request.resourceUrl())
						}
						.doOnResponse { response: HttpClientResponse, _ ->
							logger.info(
								"{} - {} {} {}",
								response.status().toString(),
								response.version(),
								response.method(),
								response.resourceUrl()
							)
						}
				)
			)
			.defaultRequest {
				it.header(Constants.HEADER_CALL_ID, MDCUtil.callIdOrNew())
				it.header(Constants.CORRELATION_ID, MDCUtil.callIdOrNew())
				it.header(Constants.NAV_CONSUMER_ID, applicationName)
				it.header(
					HttpHeaders.AUTHORIZATION,
					"Bearer ${accessToken.getAccessToken(safMaskintilmaskin, m2mPrincipalName)}",
				)
			}
	)
/*

	private fun ahentAccessTokenForSaf(): String {
		val authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(safMaskintilmaskin)
			.principal(m2mPrincipalName)
			.build()

		val authorizedClient = authorizedClientManager.authorize(authorizeRequest)
			?: throw OAuth2AuthorizationException(
				OAuth2Error(
					"invalid_token",
					"Kunne ikke hente access token for klient '$safMaskintilmaskin'. Sjekk konfigurasjon og grant-type.",
					null
				)
			)

		return authorizedClient.accessToken.tokenValue
	}
*/

}
