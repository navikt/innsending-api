package no.nav.soknad.innsending.rest.fillager

import io.mockk.clearAllMocks
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.service.DocumentService
import no.nav.soknad.innsending.service.config.ConfigDefinition
import no.nav.soknad.innsending.service.fillager.FileStorageNamespace
import no.nav.soknad.innsending.utils.ApiWebClient
import no.nav.soknad.innsending.utils.TokenGenerator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NologinRestApiTest: ApplicationTest() {

	@Autowired
	lateinit var documentService: DocumentService

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@LocalServerPort
	var serverPort: Int = 0

	var testApi: ApiWebClient? = null
	val api: ApiWebClient
		get() = testApi!!

	@BeforeEach
	fun setup() {
		testApi = ApiWebClient(webTestClient, serverPort, mockOAuth2Server)
		clearAllMocks()
		api.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "on")
			.assertSuccess()
	}

	@Test
	fun `should allow file upload V2 when main switch is on`() {
		api.uploadNologinFileV2(innsendingId = UUID.randomUUID().toString(), vedleggId = "abcdef")
			.assertSuccess()
	}

	@Test
	fun `should not allow file upload V2 when token does not contain correct role`() {
		val token = TokenGenerator(mockOAuth2Server).lagAzureM2MToken(listOf("irrelevant-role"))
		api.uploadNologinFileV2(innsendingId = UUID.randomUUID().toString(), vedleggId = "abcdef", authToken = token)
			.assertHttpStatus(HttpStatus.FORBIDDEN)
	}

	@Test
	fun `should delete only the requested no-login file and be idempotent`() {
		val innsendingsId = UUID.randomUUID()
		val attachmentId = "abcdef"
		val firstFile = api.uploadNologinFileV2(innsendingsId.toString(), attachmentId, "/litenPdf.pdf")
			.assertSuccess()
			.body
		val secondFile = api.uploadNologinFileV2(innsendingsId.toString(), attachmentId, "/litenPdf.pdf")
			.assertSuccess()
			.body

		assertNotEquals(firstFile.id, secondFile.id)
		// There is no REST GET for pre-submit NOLOGIN files, so inspect persisted state through the real service.
		assertNotNull(documentService.getFile(FileStorageNamespace.NOLOGIN, innsendingsId, firstFile.id))
		assertNotNull(documentService.getFile(FileStorageNamespace.NOLOGIN, innsendingsId, secondFile.id))

		api.deleteNologinFileV2(innsendingsId.toString(), attachmentId, firstFile.id)
			.assertHttpStatus(HttpStatus.NO_CONTENT)

		assertNull(documentService.getFile(FileStorageNamespace.NOLOGIN, innsendingsId, firstFile.id))
		assertNotNull(documentService.getFile(FileStorageNamespace.NOLOGIN, innsendingsId, secondFile.id))

		api.deleteNologinFileV2(innsendingsId.toString(), attachmentId, firstFile.id)
			.assertHttpStatus(HttpStatus.NO_CONTENT)
		assertNull(documentService.getFile(FileStorageNamespace.NOLOGIN, innsendingsId, firstFile.id))
		assertNotNull(documentService.getFile(FileStorageNamespace.NOLOGIN, innsendingsId, secondFile.id))
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
