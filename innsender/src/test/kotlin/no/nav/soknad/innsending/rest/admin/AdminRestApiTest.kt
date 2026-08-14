package no.nav.soknad.innsending.rest.admin

import com.ninjasquad.springmockk.SpykBean
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.verify
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.cleanup.TempCleanupArchiveFailure
import no.nav.soknad.innsending.utils.ApiWebClient
import no.nav.soknad.innsending.utils.TokenGenerator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

class AdminRestApiTest : ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@SpykBean
	lateinit var tempCleanupArchiveFailure: TempCleanupArchiveFailure

	@LocalServerPort
	var serverPort: Int = 0

	private var testApi: ApiWebClient? = null
	private val api: ApiWebClient
		get() = testApi!!

	@BeforeEach
	fun setup() {
		testApi = ApiWebClient(webTestClient, serverPort, mockOAuth2Server)
		clearAllMocks()
		every { tempCleanupArchiveFailure.fixAttachmentStatusAndResubmit() } returns Unit
	}

	@Test
	fun `should run cleanup job with admin scope`() {
		val response = api.runAdminJob("cleanup-klar-for-innsending")

		assertEquals(HttpStatus.CREATED, response.statusCode)
		verify(exactly = 1) { tempCleanupArchiveFailure.fixAttachmentStatusAndResubmit() }
	}

	@Test
	fun `should reject call without required scope`() {
		val token = TokenGenerator(mockOAuth2Server).lagAzureOBOToken(scopes = "random-scope")
		val response = api.runAdminJob("cleanup-klar-for-innsending", token)

		assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
	}

	@Test
	fun `should return bad request for unknown job name`() {
		val response = api.runAdminJob("unknown-job")

		assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
	}
}
