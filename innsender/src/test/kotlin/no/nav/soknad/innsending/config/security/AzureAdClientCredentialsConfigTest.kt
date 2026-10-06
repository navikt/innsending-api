package no.nav.soknad.innsending.config.security

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod

// Verifiserer at client_credentials-kall mot Azure AD (kodeverk, saf-maskintilmaskin) sender
// med client_assertion + client_assertion_type (private_key_jwt) inkl. nbf-claim, slik at Azure
// AD ikke lenger avviser kallet. Speiler TokenExchangeServiceTest for TokenX-siden.
class AzureAdClientCredentialsConfigTest {

	private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())

	@AfterEach
	fun tearDown() {
		wireMockServer.stop()
	}

	@Test
	fun `sender client_assertion, client_assertion_type og nbf i client_credentials-kall mot Azure AD`() {
		wireMockServer.start()
		wireMockServer.stubFor(
			post(urlEqualTo("/token"))
				.willReturn(
					okJson(
						"""
						{"access_token": "azuread-token", "token_type": "Bearer", "expires_in": 3600}
						""".trimIndent()
					)
				)
		)

		val properties = AzureAdProperties().apply {
			privateJwk = "src/test/resources/tokenx-jwk.json"
		}
		val azureAdClientCredentialsConfig = AzureAdClientCredentialsConfig(properties)

		val registration = ClientRegistration.withRegistrationId("kodeverk")
			.clientId("dev-gcp:team-soknad:innsending-api")
			.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
			.clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
			.tokenUri("${wireMockServer.baseUrl()}/token")
			.scope("api://dev-gcp.team-integrasjon.kodeverk/.default")
			.build()

		val grantRequest = OAuth2ClientCredentialsGrantRequest(registration)

		val tokenResponse =
			azureAdClientCredentialsConfig.accessTokenResponseClient.getTokenResponse(grantRequest)

		assertEquals("azuread-token", tokenResponse.accessToken.tokenValue)

		val requests = wireMockServer.allServeEvents
		assertEquals(1, requests.size)
		val body = requests.single().request.bodyAsString

		assertTrue(body.contains("grant_type=client_credentials"))
		assertTrue(body.contains("client_assertion_type=urn%3Aietf%3Aparams%3Aoauth%3Aclient-assertion-type%3Ajwt-bearer"))
		assertTrue(body.contains("client_assertion="))

		val clientAssertion = java.net.URLDecoder.decode(
			body.split("&").single { it.startsWith("client_assertion=") }.substringAfter("="),
			"UTF-8"
		)
		val payload = String(java.util.Base64.getUrlDecoder().decode(clientAssertion.split(".")[1]))
		assertTrue(payload.contains("\"nbf\""), "client_assertion mangler nbf-claim: $payload")
	}

	@Test
	fun `legger ikke til client_assertion for registreringer med client_secret_basic`() {
		wireMockServer.start()
		wireMockServer.stubFor(
			post(urlEqualTo("/token"))
				.willReturn(
					okJson(
						"""
						{"access_token": "azuread-token", "token_type": "Bearer", "expires_in": 3600}
						""".trimIndent()
					)
				)
		)

		val properties = AzureAdProperties().apply {
			privateJwk = "src/test/resources/tokenx-jwk.json"
		}
		val azureAdClientCredentialsConfig = AzureAdClientCredentialsConfig(properties)

		val registration = ClientRegistration.withRegistrationId("soknadsmottaker")
			.clientId("dev-gcp:team-soknad:innsending-api")
			.clientSecret("hemmelig")
			.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
			.tokenUri("${wireMockServer.baseUrl()}/token")
			.scope("api://dev-gcp.team-soknad.soknadsmottaker/.default")
			.build()

		val grantRequest = OAuth2ClientCredentialsGrantRequest(registration)

		val tokenResponse =
			azureAdClientCredentialsConfig.accessTokenResponseClient.getTokenResponse(grantRequest)

		assertEquals("azuread-token", tokenResponse.accessToken.tokenValue)

		val requests = wireMockServer.allServeEvents
		val body = requests.single().request.bodyAsString
		assertTrue(!body.contains("client_assertion="), "client_secret_basic-registrering skal ikke sende client_assertion: $body")
	}
}
