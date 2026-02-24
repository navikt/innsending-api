package no.nav.soknad.innsending.rest.fillager

import io.mockk.clearAllMocks
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.service.config.ConfigDefinition
import no.nav.soknad.innsending.utils.Api
import no.nav.soknad.innsending.utils.TokenGenerator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import java.util.UUID
import kotlin.test.assertEquals

class NologinRestApiTest: ApplicationTest() {

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
		api.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "on")
			.assertSuccess()
	}

	@Test
	fun `should allow file upload when main switch is on`() {
		api.uploadNologinFile(vedleggId = "abcdef")
			.assertSuccess()
	}

	@Test
	fun `should allow file upload V2 when main switch is on`() {
		api.uploadNologinFileV2(innsendingId = UUID.randomUUID().toString(), vedleggId = "abcdef")
			.assertSuccess()
	}

	@Test
	fun `should not allow file upload when token does not contain correct role`() {
		val token = TokenGenerator(mockOAuth2Server).lagAzureM2MToken(listOf("irrelevant-role"))
		api.uploadNologinFile(vedleggId = "abcdef", authToken = token)
			.assertHttpStatus(HttpStatus.FORBIDDEN)
	}

	@Test
	fun `should not allow file upload V2 when token does not contain correct role`() {
		val token = TokenGenerator(mockOAuth2Server).lagAzureM2MToken(listOf("irrelevant-role"))
		api.uploadNologinFileV2(innsendingId = UUID.randomUUID().toString(), vedleggId = "abcdef", authToken = token)
			.assertHttpStatus(HttpStatus.FORBIDDEN)
	}

	@Test
	fun `should not allow file upload when nologin is disabled`() {
		api.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "off")
			.assertSuccess()

		api.uploadNologinFile(vedleggId = "abcdef")
			.assertHttpStatus(HttpStatus.SERVICE_UNAVAILABLE)
			.errorBody.let { body ->
				assertEquals("temporarilyUnavailable", body.errorCode)
			}
	}

	@Test
	fun `should not allow file upload V2 when nologin is disabled`() {
		api.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "off")
			.assertSuccess()

		api.uploadNologinFileV2(innsendingId = UUID.randomUUID().toString(), vedleggId = "abcdef")
			.assertHttpStatus(HttpStatus.SERVICE_UNAVAILABLE)
			.errorBody.let { body ->
				assertEquals("temporarilyUnavailable", body.errorCode)
			}
	}

}
