package no.nav.soknad.innsending.rest

import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.SendTilPublisherTest
import no.nav.soknad.innsending.consumerapis.pdl.PdlAPI
import no.nav.soknad.innsending.consumerapis.pdl.dto.IdentDto
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerAPITest
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.model.ApplicationSubmissionResponse
import no.nav.soknad.innsending.model.BodyStatusResponseDto
import no.nav.soknad.innsending.model.KvitteringsDto
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.PatchVedleggDto
import no.nav.soknad.innsending.model.RestErrorResponseDto
import no.nav.soknad.innsending.model.SkjemaDto
import no.nav.soknad.innsending.model.SoknadFile
import no.nav.soknad.innsending.model.SubmitApplicationRequest
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.utils.ApiWebClient
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.TokenGenerator
import no.nav.soknad.innsending.utils.builders.SkjemaDokumentDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.SkjemaDtoTestBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Execution(ExecutionMode.SAME_THREAD)
class SubmissionConcurrencyTest : ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@MockkBean
	lateinit var pdl: PdlAPI

	@MockkBean
	lateinit var soknadsmottaker: MottakerAPITest

	@MockkBean
	lateinit var notifications: SendTilPublisherTest

	@LocalServerPort
	var serverPort: Int = 0

	private lateinit var api: ApiWebClient
	private lateinit var client: WebTestClient
	private lateinit var token: String

	enum class SubmissionApi {
		LEGACY,
		DIGITAL,
	}

	@BeforeEach
	fun setup() {
		clearMocks(pdl, soknadsmottaker, notifications)
		every { pdl.hentPersonIdents(any()) } answers { listOf(IdentDto(firstArg(), "FOLKEREGISTERIDENT", false)) }
		every { pdl.hentPersonData(any()) } returns null
		every { soknadsmottaker.sendInnSoknad(any(), any(), any(), any(), any()) } returns Unit
		every { notifications.avsluttBrukernotifikasjon(any()) } returns Unit
		every { notifications.opprettBrukernotifikasjon(any()) } returns Unit
		client = webTestClient.mutate()
			.baseUrl("http://localhost:$serverPort")
			.responseTimeout(Duration.ofSeconds(40))
			.build()
		api = ApiWebClient(client, serverPort, mockOAuth2Server)
		token = TokenGenerator(mockOAuth2Server).lagTokenXToken()
	}

	@ParameterizedTest
	@EnumSource(SubmissionApi::class)
	fun `deletion wins against a submission that already read the application`(submissionApi: SubmissionApi) {
		val application = createApplication()
		val id = application.innsendingsId!!

		val rejected = withPausedRequest(
			loser = { pause -> submit(application, submissionApi, pause) },
			winner = {
				val deleted = request(HttpMethod.DELETE, "/frontend/v1/soknad/$id")
				assertEquals(HttpStatus.OK, deleted.status)
				val body = body(deleted, BodyStatusResponseDto::class.java)
				assertEquals("OK", body.status)
				assertEquals("Slettet soknad med id $id", body.info)
			},
		)

		assertError(rejected, HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND)
		assertError(request(HttpMethod.GET, "/frontend/v1/soknad/$id"), HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND)
		// Both requests have completed, and rejected submissions never start asynchronous archival.
		verify(exactly = 0) {
			soknadsmottaker.sendInnSoknad(match { it.innsendingsId == id }, any(), any(), any(), any())
		}
		assertNotificationClosed(id)
	}

	@ParameterizedTest
	@EnumSource(SubmissionApi::class)
	fun `submission wins against a deletion that already read the application`(submissionApi: SubmissionApi) {
		val application = createApplication()
		val id = application.innsendingsId!!

		val rejected = withPausedRequest(
			loser = { pause -> request(HttpMethod.DELETE, "/frontend/v1/soknad/$id", pause = pause) },
			winner = { assertSubmitted(submit(application, submissionApi), application, submissionApi) },
		)

		assertError(rejected, HttpStatus.BAD_REQUEST, ErrorCode.GENERAL_ERROR)
		assertArchivedFiles(application, submissionApi)
	}

	@ParameterizedTest
	@EnumSource(SubmissionApi::class)
	fun `legacy submission rejects its stale status after another submission wins`(winningApi: SubmissionApi) {
		val application = createApplication()

		val rejected = withPausedRequest(
			loser = { pause -> submit(application, SubmissionApi.LEGACY, pause) },
			winner = { assertSubmitted(submit(application, winningApi), application, winningApi) },
		)

		assertError(rejected, HttpStatus.BAD_REQUEST, ErrorCode.APPLICATION_SENT_IN_OR_DELETED)
		assertArchivedFiles(application, winningApi)
	}

	@Test
	fun `legacy submission uses attachment status changed through REST after reading the application`() {
		val attachmentCode = "M2"
		val application = createApplication(withAttachment = true)
		val id = application.innsendingsId!!
		val attachment = api.getSoknadSendinn(id).assertSuccess().body.vedleggsListe.single { it.vedleggsnr == attachmentCode }
		api.uploadFile(id, attachment.id!!).assertHttpStatus(HttpStatus.CREATED)
		assertEquals(
			OpplastingsStatusDto.LastetOpp,
			api.getSoknadSendinn(id).assertSuccess().body.vedleggsListe.single { it.id == attachment.id }.opplastingsStatus,
		)

		val submitted = withPausedRequest(
			loser = { pause -> submit(application, SubmissionApi.LEGACY, pause) },
			winner = {
				val patched = api.patchVedlegg(id, attachment.id!!, PatchVedleggDto(opplastingsStatus = OpplastingsStatusDto.SendesIkke))
					.assertHttpStatus(HttpStatus.OK).body
				assertEquals(OpplastingsStatusDto.SendesIkke, patched.opplastingsStatus)
			},
		)

		assertSubmitted(submitted, application, SubmissionApi.LEGACY)
		val receipt = body(submitted, KvitteringsDto::class.java)
		assertFalse(receipt.innsendteVedlegg.orEmpty().any { it.vedleggsnr == attachmentCode })
		assertEquals(listOf(attachmentCode), receipt.sendesIkkeInn.orEmpty().map { it.vedleggsnr })
		assertTrue(receipt.skalEttersendes.orEmpty().isEmpty())
		assertArchivedFiles(application, SubmissionApi.LEGACY)
	}

	private fun createApplication(withAttachment: Boolean = false): SkjemaDto {
		val formNumber = "NAV 10-07.54"
		val attachments = if (withAttachment) {
			listOf(SkjemaDokumentDtoTestBuilder(vedleggsnr = "M2", pakrevd = false).build())
		} else emptyList()
		val form = SkjemaDtoTestBuilder(
			brukerId = TokenGenerator.subject,
			skjemanr = formNumber,
			tittel = "Application for a service dog",
			vedleggsListe = attachments,
		).build()
		val created = api.createSoknad(form).assertSuccess().body
		assertEquals(HttpStatus.FOUND, api.utfyltSoknad(created.innsendingsId!!, form).statusCode)
		return created
	}

	private fun submit(application: SkjemaDto, submissionApi: SubmissionApi, pause: String? = null): EntityExchangeResult<ByteArray> =
		when (submissionApi) {
			SubmissionApi.LEGACY -> request(HttpMethod.POST, "/frontend/v1/sendInn/${application.innsendingsId}", pause = pause)
			SubmissionApi.DIGITAL -> request(
				HttpMethod.POST,
				"/v1/application-digital/${application.innsendingsId}",
				SubmitApplicationRequest(
					formNumber = application.skjemanr,
					title = application.tittel,
					tema = application.tema,
					language = "nb",
					mainDocument = Hjelpemetoder.getBytesFromFile("/litenPdf.pdf"),
					mainDocumentAlt = Hjelpemetoder.getBytesFromFile("/__files/sanity.json"),
					bruker = TokenGenerator.subject,
				),
				pause,
			)
		}

	private fun request(method: HttpMethod, path: String, body: Any? = null, pause: String? = null): EntityExchangeResult<ByteArray> {
		val request = client.method(method).uri(path)
			.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
			.contentType(MediaType.APPLICATION_JSON)
		if (pause != null) request.header(PAUSE_HEADER, pause)
		if (body != null) request.bodyValue(body)
		return request.exchange().expectBody().returnResult()
	}

	private fun <T> body(response: EntityExchangeResult<ByteArray>, type: Class<T>): T =
		api.objectMapper.readValue(assertNotNull(response.responseBody), type)

	private fun assertError(response: EntityExchangeResult<ByteArray>, status: HttpStatus, code: ErrorCode? = null) {
		assertEquals(status, response.status, response.responseBody?.toString(Charsets.UTF_8))
		val error = body(response, RestErrorResponseDto::class.java)
		assertTrue(error.message.orEmpty().isNotBlank())
		assertTrue(error.errorCode.orEmpty().isNotBlank())
		if (code != null) assertEquals(code.code, error.errorCode)
	}

	private fun assertSubmitted(response: EntityExchangeResult<ByteArray>, application: SkjemaDto, submissionApi: SubmissionApi) {
		assertEquals(HttpStatus.OK, response.status, response.responseBody?.toString(Charsets.UTF_8))
		when (submissionApi) {
			SubmissionApi.LEGACY -> {
				val receipt = body(response, KvitteringsDto::class.java)
				assertEquals(application.innsendingsId, receipt.innsendingsId)
				assertTrue(assertNotNull(receipt.hoveddokumentRef).isNotBlank())
			}
			SubmissionApi.DIGITAL -> {
				val receipt = body(response, ApplicationSubmissionResponse::class.java)
				assertEquals(UUID.fromString(application.innsendingsId), receipt.innsendingsId)
				assertEquals(application.tittel, receipt.title)
				assertNotNull(receipt.mainDocumentFileId)
			}
		}
	}

	private fun assertArchivedFiles(application: SkjemaDto, submissionApi: SubmissionApi) {
		val documents = slot<List<VedleggDto>>()
		verify(timeout = WAIT_SECONDS * 1000, exactly = 1) {
			soknadsmottaker.sendInnSoknad(
				match { it.innsendingsId == application.innsendingsId && it.skjemanr == application.skjemanr && it.tittel == application.tittel },
				capture(documents),
				match { it.id == TokenGenerator.subject },
				match { it?.id == TokenGenerator.subject },
				any(),
			)
		}
		val archived = documents.captured
		assertEquals(2, archived.count { it.erHoveddokument })
		assertTrue(archived.all { it.opplastingsStatus == OpplastingsStatusDto.KlarForInnsending })
		val expectedCodes = when (submissionApi) {
			SubmissionApi.LEGACY -> listOf(application.skjemanr, application.skjemanr, Constants.KVITTERINGS_NR)
			SubmissionApi.DIGITAL -> listOf(application.skjemanr, application.skjemanr)
		}
		assertEquals(expectedCodes.sorted(), archived.map { assertNotNull(it.vedleggsnr) }.sorted())
		archived.forEach { document ->
			val files = api.hentInnsendteFiler(application.innsendingsId!!, listOf(assertNotNull(document.uuid)))
				.assertSuccess().body
			assertEquals(1, files.size)
			assertEquals(SoknadFile.FileStatus.ok, files.single().fileStatus)
			assertTrue(assertNotNull(files.single().content).isNotEmpty())
		}
		verify(exactly = 1) {
			soknadsmottaker.sendInnSoknad(match { it.innsendingsId == application.innsendingsId }, any(), any(), any(), any())
		}
		assertError(
			request(HttpMethod.GET, "/frontend/v1/soknad/${application.innsendingsId}"),
			HttpStatus.BAD_REQUEST,
			ErrorCode.APPLICATION_SENT_IN_OR_DELETED,
		)
		assertNotificationClosed(application.innsendingsId!!)
	}

	private fun assertNotificationClosed(submissionId: String) {
		// Concurrent requests can both request closure; this change does not deduplicate notifications.
		verify(timeout = WAIT_SECONDS * 1000, atLeast = 1) {
			notifications.avsluttBrukernotifikasjon(match { it.innsendingId == submissionId })
		}
	}

	private fun withPausedRequest(
		loser: (String) -> EntityExchangeResult<ByteArray>,
		winner: () -> Unit,
	): EntityExchangeResult<ByteArray> {
		val pause = UUID.randomUUID().toString()
		val entered = CountDownLatch(1)
		val release = CountDownLatch(1)
		val firstCall = AtomicBoolean(true)
		// Both controllers have read their DTO before this external call. No application transaction is held here.
		every { pdl.hentPersonIdents(any()) } answers {
			val attributes = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
			if (attributes?.request?.getHeader(PAUSE_HEADER) == pause && firstCall.compareAndSet(true, false)) {
				entered.countDown()
				check(release.await(WAIT_SECONDS, TimeUnit.SECONDS)) { "Timed out waiting to release the paused REST request" }
			}
			listOf(IdentDto(firstArg(), "FOLKEREGISTERIDENT", false))
		}
		val executor = Executors.newSingleThreadExecutor()
		val pending = executor.submit<EntityExchangeResult<ByteArray>> { loser(pause) }
		try {
			assertTrue(entered.await(WAIT_SECONDS, TimeUnit.SECONDS), "The REST request did not reach the PDL gate")
			winner()
			release.countDown()
			return pending.get(30, TimeUnit.SECONDS)
		} finally {
			release.countDown()
			pending.cancel(true)
			executor.shutdownNow()
			assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "The REST request executor did not terminate")
		}
	}

	companion object {
		private const val PAUSE_HEADER = "X-Test-Pause"
		private const val WAIT_SECONDS = 25L
	}
}
