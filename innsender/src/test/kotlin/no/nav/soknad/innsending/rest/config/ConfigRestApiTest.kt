package no.nav.soknad.innsending.rest.config

import io.mockk.clearAllMocks
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.service.config.ConfigDefinition
import no.nav.soknad.innsending.utils.ApiWebClient
import no.nav.soknad.innsending.utils.TokenGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import kotlin.test.assertNotEquals

private const val NOLOGIN_MAIN_SWITCH_DEFAULT_VALUE = "off"

private const val NOLOGIN_MAX_SUBMISSIONS_DEFAULT_VALUE = "10"

class ConfigRestApiTest : ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@LocalServerPort
	var serverPort: Int = 0

	private var testApi: ApiWebClient? = null
	private val api: ApiWebClient
		get() = testApi!!

	@BeforeEach
	fun setup() {
		testApi = ApiWebClient(webTestClient, serverPort, mockOAuth2Server)
		clearAllMocks()
		val (token, jwt) = TokenGenerator(mockOAuth2Server).lagAzureOBOTokenAndJwt(scopes = "admin-access", navIdent = "Z123456")
		`when`(azureJwtDecoder.decode(token)).thenReturn(jwt)

		api.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, NOLOGIN_MAIN_SWITCH_DEFAULT_VALUE, authToken = token)
			.assertSuccess()
		api.setConfig(ConfigDefinition.NOLOGIN_MAX_SUBMISSIONS_COUNT, NOLOGIN_MAX_SUBMISSIONS_DEFAULT_VALUE, authToken = token)
			.assertSuccess()
		api.setConfig(ConfigDefinition.NOLOGIN_MAX_SUBMISSIONS_WINDOW_MINUTES, "5", authToken = token)
			.assertSuccess()
		api.setConfig(ConfigDefinition.NOLOGIN_MAX_FILE_UPLOADS_COUNT, "20", authToken = token)
			.assertSuccess()
		api.setConfig(ConfigDefinition.NOLOGIN_MAX_FILE_UPLOADS_WINDOW_MINUTES, "20", authToken = token)
			.assertSuccess()
	}

	@Test
	fun `should update config value`() {
		val (token, jwt) = TokenGenerator(mockOAuth2Server).lagAzureOBOTokenAndJwt(scopes = "admin-access", navIdent = "Z123456")
		`when`(azureJwtDecoder.decode(token)).thenReturn(jwt)

		api.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "on", authToken = token)
			.assertSuccess()
			.body.let {
				assertEquals("on", it.value)
				assertNotEquals(NOLOGIN_MAIN_SWITCH_DEFAULT_VALUE, it.value)
			}
	}

	@Test
	fun `should fail on update when value is not one of the two allowed`() {
		val (token, jwt) = TokenGenerator(mockOAuth2Server).lagAzureOBOTokenAndJwt(scopes = "admin-access", navIdent = "Z123456")
		`when`(azureJwtDecoder.decode(token)).thenReturn(jwt)

		api.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "true", authToken = token)
			.assertClientError()
	}

	@Test
	fun `should update with new integer value`() {
		val (token, jwt) = TokenGenerator(mockOAuth2Server).lagAzureOBOTokenAndJwt(scopes = "admin-access", navIdent = "Z123456")
		`when`(azureJwtDecoder.decode(token)).thenReturn(jwt)

		val newValue = NOLOGIN_MAX_SUBMISSIONS_DEFAULT_VALUE.toInt().plus(5).toString()
		api.setConfig(ConfigDefinition.NOLOGIN_MAX_SUBMISSIONS_COUNT, newValue, authToken = token)
			.assertSuccess()
			.body.let {
				assertEquals(newValue, it.value)
			}
	}

	@Test
	fun `should fail on update when value is not an integer`() {
		val (token, jwt) = TokenGenerator(mockOAuth2Server).lagAzureOBOTokenAndJwt(scopes = "admin-access", navIdent = "Z123456")
		`when`(azureJwtDecoder.decode(token)).thenReturn(jwt)

		api.setConfig(ConfigDefinition.NOLOGIN_MAX_SUBMISSIONS_COUNT, "abc", authToken = token)
			.assertClientError()
	}

	@Test
	fun `should fail on update when value is a negative integer`() {
		val (token, jwt) = TokenGenerator(mockOAuth2Server).lagAzureOBOTokenAndJwt(scopes = "admin-access", navIdent = "Z123456")
		`when`(azureJwtDecoder.decode(token)).thenReturn(jwt)

		api.setConfig(ConfigDefinition.NOLOGIN_MAX_SUBMISSIONS_COUNT, "-5", authToken = token)
			.assertClientError()
	}

	@Test
	fun `should fail on update when new value is null`() {
		val (token, jwt) = TokenGenerator(mockOAuth2Server).lagAzureOBOTokenAndJwt(scopes = "admin-access", navIdent = "Z123456")
		`when`(azureJwtDecoder.decode(token)).thenReturn(jwt)

		api.setConfig(ConfigDefinition.NOLOGIN_MAX_SUBMISSIONS_COUNT, null, authToken = token)
			.assertClientError()
	}

	@Test
	fun `should reject token with incorrect scope`() {
		val (token, jwt) = TokenGenerator(mockOAuth2Server).lagAzureOBOTokenAndJwt(scopes = "random-scope", navIdent = "Z123456")
		`when`(azureJwtDecoder.decode(token)).thenReturn(jwt)

		api.getConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, authToken = token)
			.assertHttpStatus(HttpStatus.FORBIDDEN)
		api.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "on", token)
			.assertHttpStatus(HttpStatus.FORBIDDEN)
	}

}
