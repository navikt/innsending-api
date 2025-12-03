package no.nav.soknad.innsending.rest.config

import io.mockk.clearAllMocks
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.service.config.ConfigDefinition
import no.nav.soknad.innsending.utils.Api
import no.nav.soknad.innsending.utils.TokenGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import kotlin.test.assertNotEquals

private const val NOLOGIN_MAIN_SWITCH_DEFAULT_VALUE = "off"

private const val NOLOGIN_MAX_SUBMISSIONS_DEFAULT_VALUE = "10"

class ConfigRestApiTest : ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var restTemplate: TestRestTemplate

	@Value("\${server.port}")
	var serverPort: Int? = 9064

	var testApi: Api? = null
	val api: Api
		get() = testApi!!

	@BeforeEach
	fun setup() {
		testApi = Api(restTemplate, serverPort!!, mockOAuth2Server)
		clearAllMocks()
		api.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, NOLOGIN_MAIN_SWITCH_DEFAULT_VALUE)
			.assertSuccess()
		api.setConfig(ConfigDefinition.NOLOGIN_MAX_SUBMISSIONS_COUNT, NOLOGIN_MAX_SUBMISSIONS_DEFAULT_VALUE)
			.assertSuccess()
		api.setConfig(ConfigDefinition.NOLOGIN_MAX_SUBMISSIONS_WINDOW_MINUTES, "5")
			.assertSuccess()
		api.setConfig(ConfigDefinition.NOLOGIN_MAX_FILE_UPLOADS_COUNT, "20")
			.assertSuccess()
		api.setConfig(ConfigDefinition.NOLOGIN_MAX_FILE_UPLOADS_WINDOW_MINUTES, "20")
			.assertSuccess()
	}

	@Test
	fun `should update config value`() {
		api.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "on")
			.assertSuccess()
			.body.let {
				assertEquals("on", it.value)
				assertNotEquals(NOLOGIN_MAIN_SWITCH_DEFAULT_VALUE, it.value)
			}
	}

	@Test
	fun `should fail on update when value is not one of the two allowed`() {
		api.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "true")
			.assertClientError()
	}

	@Test
	fun `should update with new integer value`() {
		val newValue = NOLOGIN_MAX_SUBMISSIONS_DEFAULT_VALUE.toInt().plus(5).toString()
		api.setConfig(ConfigDefinition.NOLOGIN_MAX_SUBMISSIONS_COUNT, newValue)
			.assertSuccess()
			.body.let {
				assertEquals(newValue, it.value)
			}
	}

	@Test
	fun `should fail on update when value is not an integer`() {
		api.setConfig(ConfigDefinition.NOLOGIN_MAX_SUBMISSIONS_COUNT, "abc")
			.assertClientError()
	}

	@Test
	fun `should fail on update when value is a negative integer`() {
		api.setConfig(ConfigDefinition.NOLOGIN_MAX_SUBMISSIONS_COUNT, "-5")
			.assertClientError()
	}

	@Test
	fun `should fail on update when new value is null`() {
		api.setConfig(ConfigDefinition.NOLOGIN_MAX_SUBMISSIONS_COUNT, null)
			.assertClientError()
	}

	@Test
	fun `should reject token with incorrect scope`() {
		val token = TokenGenerator(mockOAuth2Server).lagAzureOBOToken(scopes = "random-scope")
		api.getConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, token)
			.assertHttpStatus(HttpStatus.FORBIDDEN)
		api.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "on", token)
			.assertHttpStatus(HttpStatus.FORBIDDEN)
	}

}
