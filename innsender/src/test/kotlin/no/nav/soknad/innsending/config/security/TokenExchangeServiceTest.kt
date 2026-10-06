package no.nav.soknad.innsending.config.security

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant

// Verifiserer at TokenExchangeService faktisk sender med client_assertion og
// client_assertion_type (private_key_jwt), i tillegg til subject_token/audience,
// slik at "Parameter client_assertion_type missing" fra TokenX ikke lenger oppstår.
class TokenExchangeServiceTest {

	private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())

	@AfterEach
	fun tearDown() {
		wireMockServer.stop()
	}

	@Test
	fun `sender client_assertion, client_assertion_type og audience i token-exchange-kall`() {
		wireMockServer.start()
		wireMockServer.stubFor(
			post(urlEqualTo("/token"))
				.willReturn(
					okJson(
						"""
						{"access_token": "utvekslet-token", "token_type": "Bearer", "expires_in": 3600}
						""".trimIndent()
					)
				)
		)

		val properties = TokenExchangeProperties().apply {
			privateJwk = "src/test/resources/tokenx-jwk.json"
			audiences = mapOf("tokenx-pdl" to "dev-fss:pdl:pdl-api")
		}
		val tokenExchangeService = TokenExchangeService(properties)

		val registration = ClientRegistration.withRegistrationId("tokenx-pdl")
			.clientId("dev-gcp:team-soknad:innsending-api")
			.authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE)
			.clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
			.tokenUri("${wireMockServer.baseUrl()}/token")
			.scope("openid")
			.build()

		val subjectJwt = Jwt.withTokenValue("innkommende-brukertoken")
			.header("alg", "none")
			.claim("sub", "12345678901")
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(60))
			.build()
		val principal = JwtAuthenticationToken(subjectJwt)

		val context = OAuth2AuthorizationContext.withClientRegistration(registration)
			.principal(principal)
			.build()

		val authorizedClient = tokenExchangeService.performJwtBearerExchange(context)

		assertNotNull(authorizedClient)
		assertEquals("utvekslet-token", authorizedClient!!.accessToken.tokenValue)

		val requests = wireMockServer.allServeEvents
		assertEquals(1, requests.size)
		val body = requests.single().request.bodyAsString

		assertTrue(body.contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Atoken-exchange"))
		assertTrue(body.contains("client_assertion_type=urn%3Aietf%3Aparams%3Aoauth%3Aclient-assertion-type%3Ajwt-bearer"))
		assertTrue(body.contains("client_assertion="))
		assertTrue(body.contains("subject_token=innkommende-brukertoken"))
		assertTrue(body.contains("subject_token_type=urn%3Aietf%3Aparams%3Aoauth%3Atoken-type%3Ajwt"))
		assertTrue(body.contains("audience=dev-fss%3Apdl%3Apdl-api"))

		// TokenX krever at client_assertion-JWT-en har en "nbf"-claim (not-before), se
		// TokenExchangeService sin jwtClientAssertionCustomizer.
		val clientAssertion = java.net.URLDecoder.decode(
			body.split("&").single { it.startsWith("client_assertion=") }.substringAfter("="),
			"UTF-8"
		)
		val payload = String(java.util.Base64.getUrlDecoder().decode(clientAssertion.split(".")[1]))
		assertTrue(payload.contains("\"nbf\""), "client_assertion mangler nbf-claim: $payload")
	}
}
