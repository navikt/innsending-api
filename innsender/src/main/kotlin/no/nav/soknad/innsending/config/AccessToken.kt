package no.nav.soknad.innsending.config

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.core.OAuth2AuthorizationException
import org.springframework.security.oauth2.core.OAuth2Error

@Configuration
@Profile("test | prod | dev")
class AccessToken (
	private val authorizedClientManager: OAuth2AuthorizedClientManager,
	){

	fun getAccessToken(registrationId: String, principalName: String?): String {
		val authorizeRequest = if (principalName != null) {
			OAuth2AuthorizeRequest.withClientRegistrationId(registrationId)
				.principal(principalName)
				.build()
		} else {
			OAuth2AuthorizeRequest.withClientRegistrationId(registrationId)
				.principal(SecurityContextHolder.getContext().authentication
					?: throw OAuth2AuthorizationException(
						OAuth2Error("invalid_principal", "Ingen autentisert bruker i SecurityContext", null)
					))
				.build()
		}

		val authorizedClient = authorizedClientManager.authorize(authorizeRequest)
			?: throw OAuth2AuthorizationException(
				OAuth2Error(
					"invalid_token",
					"Kunne ikke hente access token for klient '$registrationId'. Sjekk konfigurasjon og grant-type.",
					null
				)
			)

		return authorizedClient.accessToken.tokenValue
	}
}
