package no.nav.soknad.innsending.rest.fillager

import com.nimbusds.jwt.JWT
import com.nimbusds.jwt.JWTParser
import io.mockk.clearAllMocks
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.service.config.ConfigDefinition
import no.nav.soknad.innsending.service.config.ConfigService
import no.nav.soknad.innsending.utils.ApiWebClient
import no.nav.soknad.innsending.utils.TokenGenerator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration.jwtDecoder
import org.springframework.security.oauth2.jwt.Jwt
import java.util.UUID
import kotlin.test.assertEquals

class NologinRestApiTest: ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var configService: ConfigService

	@LocalServerPort
	var serverPort: Int = 0

	var testApi: ApiWebClient? = null
	val api: ApiWebClient
		get() = testApi!!

	@BeforeEach
	fun setup() {
		testApi = ApiWebClient(webTestClient, serverPort, mockOAuth2Server)
		clearAllMocks()
		configService.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "on", "Z123456")
	}

	@Test
	fun `should allow file upload when main switch is on`() {
		val (token, mockJwtAzure) = TokenGenerator(mockOAuth2Server).lagAzureM2MTokenAndJwt(listOf("nologin-access"), azureadUri)
		`when`(azureJwtDecoder.decode(token)).thenReturn(mockJwtAzure)

		api.uploadNologinFile(vedleggId = "abcdef", authToken = token)
			.assertSuccess()
	}

	@Test
	fun `should allow file upload V2 when main switch is on`() {
		val (token, mockJwtAzure) = TokenGenerator(mockOAuth2Server).lagAzureM2MTokenAndJwt(listOf("nologin-access"), azureadUri)
		`when`(azureJwtDecoder.decode(token)).thenReturn(mockJwtAzure)

		api.uploadNologinFileV2(innsendingId = UUID.randomUUID().toString(), vedleggId = "abcdef", authToken = token)
			.assertSuccess()
	}

	@Test
	fun `should not allow file upload when token does not contain correct role`() {
		val (token, mockJwtAzure) = TokenGenerator(mockOAuth2Server).lagAzureM2MTokenAndJwt(listOf("irrelevant-role"), azureadUri)
		`when`(azureJwtDecoder.decode(anyString())).thenReturn(mockJwtAzure)

		api.uploadNologinFile(vedleggId = "abcdef", authToken = token)
			.assertHttpStatus(HttpStatus.FORBIDDEN)
	}

	@Test
	fun `should not allow file upload V2 when token does not contain correct role`() {
		val (token, mockJwtAzure) = TokenGenerator(mockOAuth2Server).lagAzureM2MTokenAndJwt(listOf("irrelevant-role"), azureadUri)
		`when`(azureJwtDecoder.decode(token)).thenReturn(mockJwtAzure)

		api.uploadNologinFileV2(innsendingId = UUID.randomUUID().toString(), vedleggId = "abcdef", authToken = token)
			.assertHttpStatus(HttpStatus.FORBIDDEN)
	}

	@Test
	fun `should not allow file upload when nologin is disabled`() {
		configService.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "off", "Z123456")

		val (token, mockJwtAzure) = TokenGenerator(mockOAuth2Server).lagAzureM2MTokenAndJwt(listOf("nologin-access"), azureadUri)
		`when`(azureJwtDecoder.decode(token)).thenReturn(mockJwtAzure)

		api.uploadNologinFile(vedleggId = "abcdef", authToken = token)
			.assertHttpStatus(HttpStatus.SERVICE_UNAVAILABLE)
			.errorBody.let { body ->
				assertEquals("temporarilyUnavailable", body.errorCode)
			}
	}

	@Test
	fun `should not allow file upload V2 when nologin is disabled`() {
		configService.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "off", "Z123456")

		val (token, mockJwtAzure) = TokenGenerator(mockOAuth2Server).lagAzureM2MTokenAndJwt(listOf("nologin-access"), azureadUri)
		`when`(azureJwtDecoder.decode(token)).thenReturn(mockJwtAzure)

		api.uploadNologinFileV2(innsendingId = UUID.randomUUID().toString(), vedleggId = "abcdef", authToken = token)
			.assertHttpStatus(HttpStatus.SERVICE_UNAVAILABLE)
			.errorBody.let { body ->
				assertEquals("temporarilyUnavailable", body.errorCode)
			}
	}

}
