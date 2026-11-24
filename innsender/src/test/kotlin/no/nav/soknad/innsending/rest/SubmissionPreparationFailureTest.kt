package no.nav.soknad.innsending.rest

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkSpyBean
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.cleanup.LeaderSelectionLocalImpl
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerAPITest
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.model.AttachmentDto
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.Mimetype
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.SkjemaDto
import no.nav.soknad.innsending.model.SoknadFile
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.service.InnsendingService
import no.nav.soknad.innsending.service.ScheduledOperationsService
import no.nav.soknad.innsending.service.config.ConfigDefinition
import no.nav.soknad.innsending.supervision.SendInSupervision
import no.nav.soknad.innsending.util.mapping.tilleggsstonad.reiseDaglig
import no.nav.soknad.innsending.utils.ApiWebClient
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.builders.SkjemaDokumentDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.SkjemaDtoTestBuilder
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A submission that fails while it is being prepared must not leave an application that the
 * background job can forward to archiving. See https://github.com/navikt/innsending-api/issues/527.
 */
class SubmissionPreparationFailureTest : ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var scheduledOperationsService: ScheduledOperationsService

	@Autowired
	lateinit var innsendingService: InnsendingService

	@MockkSpyBean
	private lateinit var soknadsmottakerApi: MottakerAPITest

	@MockkSpyBean
	private lateinit var brukernotifikasjonPublisher: BrukernotifikasjonPublisher

	@LocalServerPort
	var serverPort: Int = 0

	private lateinit var api: ApiWebClient

	@BeforeEach
	fun setup() {
		api = ApiWebClient(webTestClient, serverPort, mockOAuth2Server)
		api.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "on").assertSuccess()
	}

	@Test
	fun `failed XML conversion leaves digital application unsubmitted and a valid retry is archived once`() {
		val soknad = createDailyTravelApplication()
		val innsendingsId = soknad.innsendingsId!!
		val attachments = listOf(uploadedAttachment(innsendingsId))
		val stateBeforeSubmission = api.getSoknadSendinn(innsendingsId).assertSuccess().body

		// U+0000 is valid JSON but invalid XML. The XML serializer rejects it with a checked exception.
		api.submitDigitalApplication(soknad, attachments, mainDocumentAlt = dailyTravelJson(addressSuffix = "\u0000"))
			.assertHttpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
			.assertErrorCode(ErrorCode.GENERAL_ERROR)

		assertApplicationUnchanged(stateBeforeSubmission)
		assertNotSelectedBySendInJob(innsendingsId)

		val response = api.submitDigitalApplication(soknad, attachments, mainDocumentAlt = dailyTravelJson())
			.assertHttpStatus(HttpStatus.OK)
			.body
		assertEquals(UUID.fromString(innsendingsId), response.innsendingsId)
		assertEquals(OpplastingsStatusDto.Innsendt, response.attachments?.single { it.attachmentCode == "M2" }?.uploadStatus)

		assertArchivedOnceWithCompleteDocuments(innsendingsId)
		verify(timeout = 5000, exactly = 1) {
			brukernotifikasjonPublisher.closeNotification(match { it.innsendingsid == innsendingsId })
		}
		api.getSoknadSendinn(innsendingsId)
			.assertHttpStatus(HttpStatus.BAD_REQUEST)
			.assertErrorCode(ErrorCode.APPLICATION_SENT_IN_OR_DELETED)
	}

	@Test
	fun `runtime failure after main documents are stored leaves digital application unsubmitted`() {
		val soknad = createDailyTravelApplication()
		val innsendingsId = soknad.innsendingsId!!
		val stateBeforeSubmission = api.getSoknadSendinn(innsendingsId).assertSuccess().body
		val unknownFile = AttachmentDto(
			attachmentCode = "M2",
			label = "Kvittering",
			uploadStatus = OpplastingsStatusDto.LastetOpp,
			fileIds = listOf(UUID.randomUUID()),
		)

		api.submitDigitalApplication(soknad, listOf(unknownFile), mainDocumentAlt = dailyTravelJson())
			.assertHttpStatus(HttpStatus.BAD_REQUEST)
			.assertErrorCode(ErrorCode.NOT_FOUND)

		assertApplicationUnchanged(stateBeforeSubmission)
		assertNotSelectedBySendInJob(innsendingsId)

		api.submitDigitalApplication(soknad, listOf(uploadedAttachment(innsendingsId)), mainDocumentAlt = dailyTravelJson())
			.assertHttpStatus(HttpStatus.OK)
		assertArchivedOnceWithCompleteDocuments(innsendingsId)
	}

	@Test
	fun `failed XML conversion of a nologin application allows a valid retry with the same id`() {
		val innsendingsId = UUID.randomUUID().toString()
		val file = api.uploadNologinFileV2(innsendingsId, "M2").assertSuccess().body
		val attachments = listOf(
			AttachmentDto(
				attachmentCode = "M2",
				label = "Kvittering",
				uploadStatus = OpplastingsStatusDto.LastetOpp,
				fileIds = listOf(file.id),
			)
		)

		api.submitNologinApplication(
			innsendingsId = innsendingsId,
			formNumber = reiseDaglig,
			tema = "TSO",
			attachments = attachments,
			mainDocumentAlt = dailyTravelJson(addressSuffix = "\u0000"),
		)
			.assertHttpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
			.assertErrorCode(ErrorCode.GENERAL_ERROR)
		assertNotSelectedBySendInJob(innsendingsId)

		// Before the fix, the failed request committed the application, so the retry was rejected as a duplicate.
		api.submitNologinApplication(
			innsendingsId = innsendingsId,
			formNumber = reiseDaglig,
			tema = "TSO",
			attachments = attachments,
			mainDocumentAlt = dailyTravelJson(),
		)
			.assertHttpStatus(HttpStatus.OK)
		assertArchivedOnceWithCompleteDocuments(innsendingsId)
	}

	@Test
	fun `background job forwards a complete application after a temporary soknadsmottaker failure`() {
		val soknad = createDailyTravelApplication()
		val innsendingsId = soknad.innsendingsId!!
		val mottakerAvailable = AtomicBoolean(false)
		every {
			soknadsmottakerApi.sendInnSoknad(match { it.innsendingsId == innsendingsId }, any(), any(), any(), any())
		} answers {
			if (!mottakerAvailable.get()) throw IllegalStateException("soknadsmottaker er midlertidig utilgjengelig")
			callOriginal()
		}

		api.submitDigitalApplication(soknad, listOf(uploadedAttachment(innsendingsId)), mainDocumentAlt = dailyTravelJson())
			.assertHttpStatus(HttpStatus.OK)
		// Await every attempt of the asynchronous forwarding, including the client's retries, before recovery.
		verify(timeout = 20_000, exactly = FORWARDING_ATTEMPTS) {
			soknadsmottakerApi.sendInnSoknad(match { it.innsendingsId == innsendingsId }, any(), any(), any(), any())
		}
		assertTrue(scheduledOperationsService.findNotSentInApplications(0).contains(innsendingsId))

		mottakerAvailable.set(true)
		runSendInSupervision()

		val documents = mutableListOf<List<VedleggDto>>()
		verify(timeout = 20_000, exactly = FORWARDING_ATTEMPTS + 1) {
			soknadsmottakerApi.sendInnSoknad(
				match { it.innsendingsId == innsendingsId },
				capture(documents),
				any(),
				any(),
				any(),
			)
		}
		assertCompleteDocuments(innsendingsId, documents.last())
		// The job marks the application as sent in the same asynchronous transaction, so it is not selected again.
		await().atMost(Duration.ofSeconds(10)).untilAsserted { assertNotSelectedBySendInJob(innsendingsId) }
	}

	private fun createDailyTravelApplication(): SkjemaDto {
		val title = "Tilleggsstønad - daglig reise"
		val skjemaDto = SkjemaDtoTestBuilder(skjemanr = reiseDaglig, tema = "TSO", tittel = title)
			.medHoveddokument(SkjemaDokumentDtoTestBuilder(tittel = title).asHovedDokument(reiseDaglig, withFile = false).build())
			.medHoveddokumentVariant(
				SkjemaDokumentDtoTestBuilder(tittel = title)
					.asHovedDokumentVariant(reiseDaglig, withFile = true, file = DAILY_TRAVEL_JSON)
					.build()
			)
			.build()
		return api.createSoknad(skjemaDto).assertSuccess().body
	}

	private fun uploadedAttachment(innsendingsId: String): AttachmentDto {
		val file = api.uploadAttachmentFile(innsendingsId, "M2").assertSuccess().body
		return AttachmentDto(
			attachmentCode = "M2",
			label = "Kvittering",
			uploadStatus = OpplastingsStatusDto.LastetOpp,
			fileIds = listOf(file.id),
		)
	}

	private fun dailyTravelJson(addressSuffix: String = ""): ByteArray {
		val mapper = jacksonObjectMapper()
		val json = mapper.readTree(Hjelpemetoder.getBytesFromFile(DAILY_TRAVEL_JSON))
		val data = json.path("data").path("data") as ObjectNode
		data.put("adresse1", data.path("adresse1").asText() + addressSuffix)
		return mapper.writeValueAsBytes(json)
	}

	private fun assertApplicationUnchanged(stateBeforeSubmission: DokumentSoknadDto) {
		val current = api.getSoknadSendinn(stateBeforeSubmission.innsendingsId!!).assertSuccess().body
		assertEquals(SoknadsStatusDto.Opprettet, current.status)
		assertEquals(null, current.innsendtDato)
		assertTrue(current.vedleggsListe.none { it.opplastingsStatus == OpplastingsStatusDto.KlarForInnsending })
		assertEquals(stateBeforeSubmission, current)
	}

	/**
	 * The background job forwards exactly the applications this query returns. With the age limit set to zero, an
	 * application left ready for sending by a failed request would be selected. Asserting the selection avoids racing
	 * the job's asynchronous forwarding.
	 */
	private fun assertNotSelectedBySendInJob(innsendingsId: String) {
		assertFalse(scheduledOperationsService.findNotSentInApplications(0).contains(innsendingsId))
	}

	/** Runs the background job with its age limit set to zero, so every application ready before now is eligible. */
	private fun runSendInSupervision() {
		SendInSupervision(
			leaderSelection = LeaderSelectionLocalImpl(),
			scheduledOperationsService = scheduledOperationsService,
			offsetMinutes = 0,
			innsendingService = innsendingService,
		).runSendInSupervision()
	}

	private fun assertArchivedOnceWithCompleteDocuments(innsendingsId: String) {
		val documents = slot<List<VedleggDto>>()
		verify(timeout = 5000, exactly = 1) {
			soknadsmottakerApi.sendInnSoknad(
				match { it.innsendingsId == innsendingsId },
				capture(documents),
				any(),
				any(),
				any(),
			)
		}
		assertCompleteDocuments(innsendingsId, documents.captured)
	}

	private fun assertCompleteDocuments(innsendingsId: String, submitted: List<VedleggDto>) {
		assertEquals(
			setOf(Mimetype.applicationSlashPdf to false, Mimetype.applicationSlashXml to true),
			submitted.filter { it.erHoveddokument }.map { it.mimetype to it.erVariant }.toSet(),
		)
		assertEquals(listOf("M2"), submitted.filterNot { it.erHoveddokument }.map { it.vedleggsnr })

		val files = api.hentInnsendteFiler(innsendingsId, submitted.map { it.uuid!! }).assertSuccess().body
		assertEquals(submitted.size, files.size)
		assertTrue(files.all { it.fileStatus == SoknadFile.FileStatus.ok && it.content?.isNotEmpty() == true })
	}

	private companion object {
		const val DAILY_TRAVEL_JSON = "/__files/dagligreise-NAV-11-12.21B-08032024.json"

		// One call plus three retries from @Retryable on MottakerInterface.sendInnSoknad.
		const val FORWARDING_ATTEMPTS = 4
	}
}
