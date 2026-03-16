package no.nav.soknad.innsending.rest

import com.ninjasquad.springmockk.SpykBean
import io.mockk.clearAllMocks
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.brukernotifikasjon.NotificationOptions
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerAPITest
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.service.NotificationService
import no.nav.soknad.innsending.service.config.ConfigDefinition
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.mapping.tilleggsstonad.stotteTilBolig
import no.nav.soknad.innsending.utils.Api
import no.nav.soknad.innsending.utils.Api.InnsendingApiResponse
import no.nav.soknad.innsending.utils.builders.SkjemaDokumentDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.SkjemaDokumentDtoV2TestBuilder
import no.nav.soknad.innsending.utils.builders.SkjemaDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.SkjemaDtoV2TestBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import java.lang.Thread.sleep
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InnsendingApiIntegrationTest : ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var restTemplate: TestRestTemplate

	@SpykBean
	private lateinit var soknadsmottakerApi: MottakerAPITest

	@SpykBean
	private lateinit var notificationService: NotificationService

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
	fun testApplicationWithAttachmentFilesInDb() {
		val skjemanr = "NAV 10-07.54"
		val vedleggsnrM2 = "M2"
		val vedleggsnrM5 = "M5"
		val skjematittel = "Søknad om servicehund"
		val hoveddokument =
			SkjemaDokumentDtoTestBuilder(tittel = skjematittel).asHovedDokument(skjemanr, withFile = false).build()
		val hoveddokumentVariant =
			SkjemaDokumentDtoTestBuilder(tittel = skjematittel).asHovedDokumentVariant(skjemanr, withFile = true).build()

		val skjemaDto = SkjemaDtoTestBuilder(skjemanr = skjemanr, tittel = skjematittel)
			.medHoveddokument(hoveddokument)
			.medHoveddokumentVariant(hoveddokumentVariant)
			.build()

		// Opprett søknad
		val soknad = api.createSoknad(skjemaDto)
			.assertSuccess()
			.body
		val innsendingsId = soknad.innsendingsId!!

		// Opprett vedlegg og hoveddokument
		val vedleggM2 = SkjemaDokumentDtoTestBuilder(vedleggsnr = vedleggsnrM2, tittel = "Vedlegg M2").build()
		val vedleggM5 = SkjemaDokumentDtoTestBuilder(vedleggsnr = vedleggsnrM5, tittel = "Vedlegg M5").build()
		val hoveddokumentWithFile =
			SkjemaDokumentDtoTestBuilder(tittel = skjematittel).asHovedDokument(skjemanr, withFile = true).build()
		val updatedSoknad = skjemaDto.copy(
			hoveddokument = hoveddokumentWithFile,
			vedleggsListe = listOf(vedleggM2, vedleggM5)
		)
		api.utfyltSoknad(innsendingsId, updatedSoknad)

		val vedleggsIdM2 = api.getSoknadSendinn(innsendingsId)
			.assertSuccess()
			.body.vedleggsListe.first { it.vedleggsnr == vedleggsnrM2 }.id!!

		// Last opp to filer til vedlegg M2
		api.uploadFile(innsendingsId, vedleggsIdM2)
			.assertHttpStatus(HttpStatus.CREATED)
		api.uploadFile(innsendingsId, vedleggsIdM2)
			.assertHttpStatus(HttpStatus.CREATED)

		val kvittering = api.sendInnSoknad(innsendingsId)
			.assertSuccess()
			.body

		// verify response
		assertNotNull(kvittering.innsendteVedlegg?.firstOrNull { it.vedleggsnr == vedleggsnrM2 })
		assertNull(kvittering.innsendteVedlegg?.firstOrNull { it.vedleggsnr == vedleggsnrM5 })
		assertNotNull(kvittering.skalEttersendes?.firstOrNull { it.vedleggsnr == vedleggsnrM5 })

		// verify notifications
		val slotEttersendingsId = mutableListOf<String>()
		val slotNotificationOpts = mutableListOf<NotificationOptions>()
		verify(exactly = 2) {
			notificationService.create(
				capture(slotEttersendingsId),
				capture(slotNotificationOpts)
			)
		}
		assertEquals(innsendingsId, slotEttersendingsId.firstOrNull())
		val ettersendingsId = slotEttersendingsId[1]
		assertNotNull(ettersendingsId, "Expected creation of notification for ettersending")

		verify(exactly = 1) { notificationService.close(innsendingsId) }

		// verify invocation of soknadsmottaker
		val slotSoknad = slot<DokumentSoknadDto>()
		val slotVedleggsliste = slot<List<VedleggDto>>()
		val slotAvsender = slot<AvsenderDto>()
		val slotBruker = slot<BrukerDto?>()
		sleep(1000)
		verify(exactly = 1) {
			soknadsmottakerApi.sendInnSoknad(
				capture(slotSoknad),
				capture(slotVedleggsliste),
				capture(slotAvsender),
				captureNullable(slotBruker)
			)
		}

		assertEquals(innsendingsId, slotSoknad.captured.innsendingsId)
		val innsendteDokumenter = slotVedleggsliste.captured
		assertEquals(4, innsendteDokumenter.size)
		assertTrue(innsendteDokumenter.all { it.mimetype != null })

		val innsendingskvittering = innsendteDokumenter.firstOrNull { it.vedleggsnr == Constants.KVITTERINGS_NR }
		assertNotNull(innsendingskvittering)
		assertEquals(OpplastingsStatusDto.KlarForInnsending, innsendingskvittering.opplastingsStatus)

		val innsendtM2 = innsendteDokumenter.firstOrNull { it.vedleggsnr == vedleggsnrM2 }
		assertNotNull(innsendtM2)
		assertEquals(OpplastingsStatusDto.KlarForInnsending, innsendtM2.opplastingsStatus)

		val hoveddokumentListe = innsendteDokumenter.filter { it.erHoveddokument }
		assertEquals(2, hoveddokumentListe.size)
		assertTrue { hoveddokumentListe.all { it.opplastingsStatus == OpplastingsStatusDto.KlarForInnsending } }

		// verify fetching of files from soknadsarkiverer
		innsendteDokumenter.forEach { submittedAttachment ->
			val attachmentUuid = submittedAttachment.uuid!!
			val files = api.hentInnsendteFiler(innsendingsId, listOf(attachmentUuid))
				.assertSuccess()
				.body
			assertEquals(
				1,
				files.size,
				"For attachment ${submittedAttachment.vedleggsnr}, expected to find exactly 1 file"
			)
			assertEquals(
				SoknadFile.FileStatus.ok,
				files[0].fileStatus,
				"For attachment ${submittedAttachment.vedleggsnr}, expected file status to be ok"
			)
			assertNotNull(
				files[0].content,
				"For attachment ${submittedAttachment.vedleggsnr}, expected file content to be not null"
			)
		}
	}

	@Test
	fun testTilleggstotteApplicationWithAttachmentFilesInDb() {
		val skjemanr = stotteTilBolig
		val skjematittel = "Tilleggsstønad - støtte til bolig og overnatting"
		val hoveddokument =
			SkjemaDokumentDtoTestBuilder(tittel = skjematittel).asHovedDokument(skjemanr, withFile = true).build()
		val mainDocumentAltPath = "/__files/TSR-NAV111219B-submission.json"
		val hoveddokumentVariant =
			SkjemaDokumentDtoTestBuilder(tittel = skjematittel).asHovedDokumentVariant(
				skjemanr,
				withFile = true,
				file = mainDocumentAltPath
			).build()

		val skjemaDto = SkjemaDtoTestBuilder(skjemanr = skjemanr, tema = "TSR", tittel = skjematittel)
			.medHoveddokument(hoveddokument)
			.medHoveddokumentVariant(hoveddokumentVariant)
			.build()

		val soknad = api.createSoknad(skjemaDto)
			.assertSuccess()
			.body
		val innsendingsId = soknad.innsendingsId!!

		api.utfyltSoknad(innsendingsId, skjemaDto)
		val kvittering = api.sendInnSoknad(innsendingsId)
			.assertSuccess()
			.body

		assertNotNull(kvittering.hoveddokumentRef)

		// verify invocation of soknadsmottaker
		val slotSoknad = slot<DokumentSoknadDto>()
		val slotVedleggsliste = slot<List<VedleggDto>>()
		val slotAvsender = slot<AvsenderDto>()
		val slotBruker = slot<BrukerDto?>()
		sleep(1000)
		verify(exactly = 1) {
			soknadsmottakerApi.sendInnSoknad(
				capture(slotSoknad),
				capture(slotVedleggsliste),
				capture(slotAvsender),
				captureNullable(slotBruker)
			)
		}

		assertEquals(innsendingsId, slotSoknad.captured.innsendingsId)
		val innsendteDokumenter = slotVedleggsliste.captured
		assertEquals(3, innsendteDokumenter.size)
		assertTrue(innsendteDokumenter.all { it.mimetype != null })

		val innsendingskvittering = innsendteDokumenter.firstOrNull { it.vedleggsnr == Constants.KVITTERINGS_NR }
		assertNotNull(innsendingskvittering)

		val hoveddokumentListe = innsendteDokumenter.filter { it.erHoveddokument }
		assertEquals(2, hoveddokumentListe.size)
		assertTrue { hoveddokumentListe.all { it.opplastingsStatus == OpplastingsStatusDto.KlarForInnsending } }

		val soknadspdf = hoveddokumentListe.first { !it.erVariant }
		assertEquals(Mimetype.applicationSlashPdf, soknadspdf.mimetype)
		val variant = hoveddokumentListe.first { it.erVariant }
		assertEquals(Mimetype.applicationSlashXml, variant.mimetype)

		// verify fetching of files from soknadsarkiverer
		innsendteDokumenter.forEach { submittedAttachment ->
			val attachmentUuid = submittedAttachment.uuid!!
			val files = api.hentInnsendteFiler(innsendingsId, listOf(attachmentUuid))
				.assertSuccess()
				.body
			assertEquals(
				1,
				files.size,
				"For attachment ${submittedAttachment.vedleggsnr}, expected to find exactly 1 file"
			)
			assertEquals(
				SoknadFile.FileStatus.ok,
				files[0].fileStatus,
				"For attachment ${submittedAttachment.vedleggsnr}, expected file status to be ok"
			)
			assertNotNull(
				files[0].content,
				"For attachment ${submittedAttachment.vedleggsnr}, expected file content to be not null"
			)
		}
	}

	@Test
	fun testApplicationWithAttachmentFilesInBucket() {
		val skjemanr = "NAV 10-07.54"
		val skjematittel = "Søknad om servicehund"
		val hoveddokument =
			SkjemaDokumentDtoTestBuilder(tittel = skjematittel).asHovedDokument(skjemanr, withFile = false).build()
		val hoveddokumentVariant =
			SkjemaDokumentDtoTestBuilder(tittel = skjematittel).asHovedDokumentVariant(skjemanr, withFile = true).build()

		val skjemaDto = SkjemaDtoTestBuilder(skjemanr = skjemanr, tittel = skjematittel)
			.medHoveddokument(hoveddokument)
			.medHoveddokumentVariant(hoveddokumentVariant)
			.build()

		val soknad = api.createSoknad(skjemaDto)
			.assertSuccess()
			.body
		val innsendingsId = soknad.innsendingsId!!

		val fileM2part1 = api.uploadAttachmentFile(innsendingsId, "M2")
			.assertSuccess()
			.body
		val fileM2part2 = api.uploadAttachmentFile(innsendingsId, "M2")
			.assertSuccess()
			.body

		val fileM3 = api.uploadAttachmentFile(innsendingsId, "M3")
			.assertSuccess()
			.body

		val attachments = listOf(
			AttachmentDto(
				attachmentCode = "M2",
				"Medisinske utgifter",
				OpplastingsStatusDto.LastetOpp,
				fileIds = listOf(fileM2part1.id, fileM2part2.id)
			),
			AttachmentDto(
				attachmentCode = "M3",
				"Legeerklæring",
				OpplastingsStatusDto.LastetOpp,
				fileIds = listOf(fileM3.id)
			),
			AttachmentDto(attachmentCode = "M4", "Kursbevis", OpplastingsStatusDto.SendesAvAndre),
			AttachmentDto(attachmentCode = "M5", "Leiekontrakt", OpplastingsStatusDto.SendSenere),
		)
		val submissionResponse = api.submitDigitalApplication(soknad, attachments)
			.assertSuccess()
			.body

		// verify response
		assertEquals(4, submissionResponse.attachments?.size)
		assertEquals(
			OpplastingsStatusDto.Innsendt,
			submissionResponse.attachments?.first { it.attachmentCode == "M2" }?.uploadStatus
		)
		assertEquals(
			OpplastingsStatusDto.Innsendt,
			submissionResponse.attachments?.first { it.attachmentCode == "M3" }?.uploadStatus
		)
		assertEquals(
			OpplastingsStatusDto.SendesAvAndre,
			submissionResponse.attachments?.first { it.attachmentCode == "M4" }?.uploadStatus
		)
		assertEquals(
			OpplastingsStatusDto.SendSenere,
			submissionResponse.attachments?.first { it.attachmentCode == "M5" }?.uploadStatus
		)
		assertNotNull(submissionResponse.ettersendingsId)

		// verify notifications
		val slotEttersendingsId = mutableListOf<String>()
		val slotNotificationOpts = mutableListOf<NotificationOptions>()
		verify(exactly = 2) {
			notificationService.create(
				capture(slotEttersendingsId),
				capture(slotNotificationOpts)
			)
		}
		assertEquals(innsendingsId, slotEttersendingsId.firstOrNull())
		val ettersendingsId = slotEttersendingsId[1]
		assertNotNull(ettersendingsId)
		assertEquals(submissionResponse.ettersendingsId?.toString(), ettersendingsId)

		verify(exactly = 1) { notificationService.close(innsendingsId) }

		// verify invocation of soknadsmottaker
		val slotSoknad = slot<DokumentSoknadDto>()
		val slotVedleggsliste = slot<List<VedleggDto>>()
		val slotAvsender = slot<AvsenderDto>()
		val slotBruker = slot<BrukerDto?>()
		sleep(1000)
		verify(exactly = 1) {
			soknadsmottakerApi.sendInnSoknad(
				capture(slotSoknad),
				capture(slotVedleggsliste),
				capture(slotAvsender),
				captureNullable(slotBruker)
			)
		}

		assertEquals(innsendingsId, slotSoknad.captured.innsendingsId)
		val innsendteDokumenter = slotVedleggsliste.captured
		assertEquals(4, innsendteDokumenter.size)
		assertTrue(innsendteDokumenter.all { it.mimetype != null })

		val innsendtM2 = innsendteDokumenter.firstOrNull { it.vedleggsnr == "M2" }
		assertNotNull(innsendtM2)
		assertEquals(OpplastingsStatusDto.KlarForInnsending, innsendtM2.opplastingsStatus)

		val innsendtM3 = innsendteDokumenter.firstOrNull { it.vedleggsnr == "M3" }
		assertNotNull(innsendtM3)
		assertEquals(OpplastingsStatusDto.KlarForInnsending, innsendtM3.opplastingsStatus)

		val vedleggM4 = innsendteDokumenter.firstOrNull { it.vedleggsnr == "M4" }
		assertNull(vedleggM4)

		val hoveddokumentListe = innsendteDokumenter.filter { it.erHoveddokument }
		assertEquals(2, hoveddokumentListe.size)
		assertTrue { hoveddokumentListe.all { it.opplastingsStatus == OpplastingsStatusDto.LastetOpp || it.opplastingsStatus == OpplastingsStatusDto.KlarForInnsending } }

		// verify fetching of files from soknadsarkiverer
		innsendteDokumenter.forEach { submittedAttachment ->
			val attachmentUuid = submittedAttachment.uuid!!
			val files = api.hentInnsendteFiler(innsendingsId, listOf(attachmentUuid))
				.assertSuccess()
				.body
			assertEquals(
				1,
				files.size,
				"For attachment ${submittedAttachment.vedleggsnr}, expected to find exactly 1 file"
			)
			assertEquals(
				SoknadFile.FileStatus.ok,
				files[0].fileStatus,
				"For attachment ${submittedAttachment.vedleggsnr}, expected file status to be ok"
			)
			assertNotNull(
				files[0].content,
				"For attachment ${submittedAttachment.vedleggsnr}, expected file content to be not null"
			)
		}

		// verify fetching of file with unknown uuid
		val filesUnknownAttachment = api.hentInnsendteFiler(innsendingsId, listOf(UUID.randomUUID().toString()))
			.assertSuccess()
			.body
		assertEquals(1, filesUnknownAttachment.size)
		assertEquals(SoknadFile.FileStatus.notfound, filesUnknownAttachment[0].fileStatus)
	}


	@Test
	fun testAtAndreGangsInnsendingFeiler() {
		val skjemanr = "NAV 10-07.54"
		val skjematittel = "Søknad om servicehund"
		val hoveddokument =
			SkjemaDokumentDtoTestBuilder(tittel = skjematittel).asHovedDokument(skjemanr, withFile = false).build()
		val hoveddokumentVariant =
			SkjemaDokumentDtoTestBuilder(tittel = skjematittel).asHovedDokumentVariant(skjemanr, withFile = true).build()

		val skjemaDto = SkjemaDtoTestBuilder(skjemanr = skjemanr, tittel = skjematittel)
			.medHoveddokument(hoveddokument)
			.medHoveddokumentVariant(hoveddokumentVariant)
			.build()

		val soknad = api.createSoknad(skjemaDto)
			.assertSuccess()
			.body
		val innsendingsId = soknad.innsendingsId!!

		val fileM2part1 = api.uploadAttachmentFile(innsendingsId, "M2")
			.assertSuccess()
			.body
		val fileM2part2 = api.uploadAttachmentFile(innsendingsId, "M2")
			.assertSuccess()
			.body

		val fileM3 = api.uploadAttachmentFile(innsendingsId, "M3")
			.assertSuccess()
			.body

		val attachments = listOf(
			AttachmentDto(
				attachmentCode = "M2",
				"Medisinske utgifter",
				OpplastingsStatusDto.LastetOpp,
				fileIds = listOf(fileM2part1.id, fileM2part2.id)
			),
			AttachmentDto(
				attachmentCode = "M3",
				"Legeerklæring",
				OpplastingsStatusDto.LastetOpp,
				fileIds = listOf(fileM3.id)
			),
			AttachmentDto(attachmentCode = "M4", "Kursbevis", OpplastingsStatusDto.SendesAvAndre),
			AttachmentDto(attachmentCode = "M5", "Leiekontrakt", OpplastingsStatusDto.SendSenere),
		)
		val submissionResponse = api.submitDigitalApplication(soknad, attachments)
			.assertSuccess()
			.body

		// verify response
		assertEquals(4, submissionResponse.attachments?.size)
		assertTrue(submissionResponse.mainDocumentFileId != null)

		val submissionRespons2e = api.submitDigitalApplication(soknad, attachments)
			.assertClientError()
			.errorBody.let {
				assertEquals("illegalAction.applicationSentInOrDeleted", it.errorCode)
				assertEquals("Søknaden kan ikke vises. Søknaden er slettet eller innsendt og kan ikke vises eller endres.", it.message)
			}

	}


		@Test
	fun testTilleggstonadApplicationWithAttachmentFilesInBucket() {
		val skjemanr = stotteTilBolig
		val skjematittel = "Tilleggsstønad - støtte til bolig og overnatting"
		val hoveddokument =
			SkjemaDokumentDtoTestBuilder(tittel = skjematittel).asHovedDokument(skjemanr, withFile = false).build()
		val mainDocumentAltPath = "/__files/TSR-NAV111219B-submission.json"
		val hoveddokumentVariant =
			SkjemaDokumentDtoTestBuilder(tittel = skjematittel).asHovedDokumentVariant(
				skjemanr,
				withFile = true,
				file = mainDocumentAltPath
			).build()

		val skjemaDto = SkjemaDtoTestBuilder(skjemanr = skjemanr, tema = "TSR", tittel = skjematittel)
			.medHoveddokument(hoveddokument)
			.medHoveddokumentVariant(hoveddokumentVariant)
			.build()

		val soknad = api.createSoknad(skjemaDto)
			.assertSuccess()
			.body
		val innsendingsId = soknad.innsendingsId!!

		val fileM2 = api.uploadAttachmentFile(innsendingsId, "M2")
			.assertSuccess()
			.body

		val attachments = listOf(
			AttachmentDto(
				attachmentCode = "M2",
				"Medisinske utgifter",
				OpplastingsStatusDto.LastetOpp,
				fileIds = listOf(fileM2.id)
			),
		)
		val submissionResponse = api.submitDigitalApplication(
			soknad,
			attachments,
			mainDocumentAltPath = mainDocumentAltPath
		)
			.assertSuccess()
			.body

		// verify response
		assertEquals(1, submissionResponse.attachments?.size)
		assertEquals(
			OpplastingsStatusDto.Innsendt,
			submissionResponse.attachments?.first { it.attachmentCode == "M2" }?.uploadStatus
		)
		assertNull(submissionResponse.ettersendingsId)

		// verify notifications
		val slotEttersendingsId = mutableListOf<String>()
		val slotNotificationOpts = mutableListOf<NotificationOptions>()
		verify(exactly = 1) {
			notificationService.create(
				capture(slotEttersendingsId),
				capture(slotNotificationOpts)
			)
		}
		assertEquals(innsendingsId, slotEttersendingsId.firstOrNull())

		verify(exactly = 1) { notificationService.close(innsendingsId) }

		// verify invocation of soknadsmottaker
		val slotSoknad = slot<DokumentSoknadDto>()
		val slotVedleggsliste = slot<List<VedleggDto>>()
		val slotAvsender = slot<AvsenderDto>()
		val slotBruker = slot<BrukerDto?>()
		sleep(1000)
		verify(exactly = 1) {
			soknadsmottakerApi.sendInnSoknad(
				capture(slotSoknad),
				capture(slotVedleggsliste),
				capture(slotAvsender),
				captureNullable(slotBruker)
			)
		}

		assertEquals(innsendingsId, slotSoknad.captured.innsendingsId)
		val innsendteDokumenter = slotVedleggsliste.captured
		assertEquals(3, innsendteDokumenter.size)
		assertTrue(innsendteDokumenter.all { it.mimetype != null })

		val innsendtM2 = innsendteDokumenter.firstOrNull { it.vedleggsnr == "M2" }
		assertNotNull(innsendtM2)
		assertEquals(OpplastingsStatusDto.KlarForInnsending, innsendtM2.opplastingsStatus)

		val hoveddokumentListe = innsendteDokumenter.filter { it.erHoveddokument }
		assertEquals(2, hoveddokumentListe.size)
		assertTrue { hoveddokumentListe.all { it.opplastingsStatus == OpplastingsStatusDto.KlarForInnsending } }

		val soknadspdf = hoveddokumentListe.first { !it.erVariant }
		assertEquals(Mimetype.applicationSlashPdf, soknadspdf.mimetype)
		val variant = hoveddokumentListe.first { it.erVariant }
		assertEquals(Mimetype.applicationSlashXml, variant.mimetype)

		// verify fetching of files from soknadsarkiverer
		innsendteDokumenter.forEach { submittedAttachment ->
			val attachmentUuid = submittedAttachment.uuid!!
			val files = api.hentInnsendteFiler(innsendingsId, listOf(attachmentUuid))
				.assertSuccess()
				.body
			assertEquals(
				1,
				files.size,
				"For attachment ${submittedAttachment.vedleggsnr}, expected to find exactly 1 file"
			)
			assertEquals(
				SoknadFile.FileStatus.ok,
				files[0].fileStatus,
				"For attachment ${submittedAttachment.vedleggsnr}, expected file status to be ok"
			)
			assertNotNull(
				files[0].content,
				"For attachment ${submittedAttachment.vedleggsnr}, expected file content to be not null"
			)
		}
	}

	@Test
	fun testNologinApplicationWithAttachmentFilesCopiedToDb() {
		val innsendingsId = UUID.randomUUID().toString()

		val fileM2part1 = api.uploadNologinFileV2(innsendingsId, "M2")
			.assertSuccess()
			.body
		val fileM2part2 = api.uploadNologinFileV2(innsendingsId, "M2")
			.assertSuccess()
			.body

		val fileM3 = api.uploadNologinFileV2(innsendingsId, "M3")
			.assertSuccess()
			.body

		val vedleggM2 = SkjemaDokumentDtoV2TestBuilder(
			opplastingsStatus = OpplastingsStatusDto.LastetOpp,
			mimetype = Mimetype.applicationSlashPdf,
			filIdListe = listOf(fileM2part1.id.toString(), fileM2part2.id.toString()),
			vedleggsnr = "M2",
		).build()

		val vedleggM3 = SkjemaDokumentDtoV2TestBuilder(
			opplastingsStatus = OpplastingsStatusDto.LastetOpp,
			mimetype = Mimetype.applicationSlashPdf,
			filIdListe = listOf(fileM3.id.toString()),
			vedleggsnr = "M3",
		).build()

		val skjemaDto = SkjemaDtoV2TestBuilder()
			.medAvsender("Are Avsender")
			.medInnsendingsId(innsendingsId)
			.medVedlegg(listOf(vedleggM2, vedleggM3))
			.build()

		val kvittering = api.sendInnNologinSoknad(skjemaDto)
			.assertSuccess()
			.body

		// verify response
		assertEquals(2, kvittering.innsendteVedlegg?.size)
		assertNotNull(kvittering.innsendteVedlegg?.first { it.vedleggsnr == "M2" })
		assertNotNull(kvittering.innsendteVedlegg?.first { it.vedleggsnr == "M3" })

		// verify notifications
		verify(exactly = 0) { notificationService.create(any(), any()) }
		verify(exactly = 0) { notificationService.close(any()) }

		// verify invocation of soknadsmottaker
		val slotSoknad = slot<DokumentSoknadDto>()
		val slotVedleggsliste = slot<List<VedleggDto>>()
		val slotAvsender = slot<AvsenderDto>()
		val slotBruker = slot<BrukerDto?>()
		sleep(1000)
		verify(exactly = 1) {
			soknadsmottakerApi.sendInnSoknad(
				capture(slotSoknad),
				capture(slotVedleggsliste),
				capture(slotAvsender),
				captureNullable(slotBruker)
			)
		}

		assertEquals(innsendingsId, slotSoknad.captured.innsendingsId)
		val innsendteDokumenter = slotVedleggsliste.captured
		assertEquals(5, innsendteDokumenter.size)
		assertTrue(innsendteDokumenter.all { it.mimetype != null })

		val innsendtM2 = innsendteDokumenter.firstOrNull { it.vedleggsnr == "M2" }
		assertNotNull(innsendtM2)
		assertEquals(OpplastingsStatusDto.KlarForInnsending, innsendtM2.opplastingsStatus)

		val innsendtM3 = innsendteDokumenter.firstOrNull { it.vedleggsnr == "M3" }
		assertNotNull(innsendtM3)
		assertEquals(OpplastingsStatusDto.KlarForInnsending, innsendtM2.opplastingsStatus)

		val innsendtL7 = innsendteDokumenter.firstOrNull { it.vedleggsnr == "L7" }
		assertNotNull(innsendtL7)
		assertEquals(OpplastingsStatusDto.KlarForInnsending, innsendtL7.opplastingsStatus)

		val hoveddokumentListe = innsendteDokumenter.filter { it.erHoveddokument }
		assertEquals(2, hoveddokumentListe.size)
		assertTrue { hoveddokumentListe.all { it.opplastingsStatus == OpplastingsStatusDto.KlarForInnsending } }

		// verify fetching of files from soknadsarkiverer
		innsendteDokumenter.forEach { submittedAttachment ->
			val attachmentUuid = submittedAttachment.uuid!!
			val files = api.hentInnsendteFiler(innsendingsId, listOf(attachmentUuid))
				.assertSuccess()
				.body
			assertEquals(
				1,
				files.size,
				"For attachment ${submittedAttachment.vedleggsnr}, expected to find exactly 1 file"
			)
			assertEquals(
				SoknadFile.FileStatus.ok,
				files[0].fileStatus,
				"For attachment ${submittedAttachment.vedleggsnr}, expected file status to be ok"
			)
			assertNotNull(
				files[0].content,
				"For attachment ${submittedAttachment.vedleggsnr}, expected file content to be not null"
			)
		}

		// verify fetching of file with unknown uuid
		val filesUnknownAttachment = api.hentInnsendteFiler(innsendingsId, listOf(UUID.randomUUID().toString()))
			.assertSuccess()
			.body
		assertEquals(1, filesUnknownAttachment.size)
		assertEquals(SoknadFile.FileStatus.notfound, filesUnknownAttachment[0].fileStatus)
	}

	@Test
	fun testNologinApplicationWithAttachmentFilesInBucket() {
		val skjemanr = "NAV 10-07.54"
		val skjematittel = "Søknad om servicehund"

		val innsendingsId = UUID.randomUUID().toString()

		val fileM2part1 = api.uploadNologinFileV2(innsendingsId, "M2")
			.assertSuccess()
			.body
		val fileM2part2 = api.uploadNologinFileV2(innsendingsId, "M2")
			.assertSuccess()
			.body

		val fileM3 = api.uploadNologinFileV2(innsendingsId, "M3")
			.assertSuccess()
			.body

		val attachments = listOf(
			AttachmentDto(
				attachmentCode = "M2",
				"Medisinske utgifter",
				OpplastingsStatusDto.LastetOpp,
				fileIds = listOf(fileM2part1.id, fileM2part2.id)
			),
			AttachmentDto(
				attachmentCode = "M3",
				"Legeerklæring",
				OpplastingsStatusDto.LastetOpp,
				fileIds = listOf(fileM3.id)
			),
			AttachmentDto(attachmentCode = "M4", "Kursbevis", OpplastingsStatusDto.SendesAvAndre),
			AttachmentDto(attachmentCode = "M5", "Leiekontrakt", OpplastingsStatusDto.SendSenere),
		)
		val submissionResponse = api.submitNologinApplication(
			innsendingsId = innsendingsId,
			formNumber = skjemanr,
			title = skjematittel,
			attachments = attachments
		)
			.assertSuccess()
			.body

		// verify response
		assertEquals(4, submissionResponse.attachments?.size)
		assertEquals(
			OpplastingsStatusDto.Innsendt,
			submissionResponse.attachments?.first { it.attachmentCode == "M2" }?.uploadStatus
		)
		assertEquals(
			OpplastingsStatusDto.Innsendt,
			submissionResponse.attachments?.first { it.attachmentCode == "M3" }?.uploadStatus
		)
		assertEquals(
			OpplastingsStatusDto.SendesAvAndre,
			submissionResponse.attachments?.first { it.attachmentCode == "M4" }?.uploadStatus
		)
		assertEquals(
			OpplastingsStatusDto.SendSenere,
			submissionResponse.attachments?.first { it.attachmentCode == "M5" }?.uploadStatus
		)
		assertNull(submissionResponse.ettersendingsId)

		// verify notifications
		verify(exactly = 0) { notificationService.create(any(), any()) }
		verify(exactly = 0) { notificationService.close(any()) }

		// verify invocation of soknadsmottaker
		val slotSoknad = slot<DokumentSoknadDto>()
		val slotVedleggsliste = slot<List<VedleggDto>>()
		val slotAvsender = slot<AvsenderDto>()
		val slotBruker = slot<BrukerDto?>()
		sleep(1000)
		verify(exactly = 1) {
			soknadsmottakerApi.sendInnSoknad(
				capture(slotSoknad),
				capture(slotVedleggsliste),
				capture(slotAvsender),
				captureNullable(slotBruker)
			)
		}

		assertEquals(innsendingsId, slotSoknad.captured.innsendingsId)
		val innsendteDokumenter = slotVedleggsliste.captured
		assertEquals(4, innsendteDokumenter.size)
		assertTrue(innsendteDokumenter.all { it.mimetype != null })

		val innsendtM2 = innsendteDokumenter.firstOrNull { it.vedleggsnr == "M2" }
		assertNotNull(innsendtM2)
		assertEquals(OpplastingsStatusDto.KlarForInnsending, innsendtM2.opplastingsStatus)

		val innsendtM3 = innsendteDokumenter.firstOrNull { it.vedleggsnr == "M3" }
		assertNotNull(innsendtM3)
		assertEquals(OpplastingsStatusDto.KlarForInnsending, innsendtM2.opplastingsStatus)

		val vedleggM4 = innsendteDokumenter.firstOrNull { it.vedleggsnr == "M4" }
		assertNull(vedleggM4)

		val hoveddokumentListe = innsendteDokumenter.filter { it.erHoveddokument }
		assertEquals(2, hoveddokumentListe.size)
		assertTrue { hoveddokumentListe.all { it.opplastingsStatus == OpplastingsStatusDto.KlarForInnsending } }

		// verify fetching of files from soknadsarkiverer
		innsendteDokumenter.forEach { submittedAttachment ->
			val attachmentUuid = submittedAttachment.uuid!!
			val files = api.hentInnsendteFiler(innsendingsId, listOf(attachmentUuid))
				.assertSuccess()
				.body
			assertEquals(
				1,
				files.size,
				"For attachment ${submittedAttachment.vedleggsnr}, expected to find exactly 1 file"
			)
			assertEquals(
				SoknadFile.FileStatus.ok,
				files[0].fileStatus,
				"For attachment ${submittedAttachment.vedleggsnr}, expected file status to be ok"
			)
			assertNotNull(
				files[0].content,
				"For attachment ${submittedAttachment.vedleggsnr}, expected file content to be not null"
			)
		}

		// verify fetching of file with unknown uuid
		val filesUnknownAttachment = api.hentInnsendteFiler(innsendingsId, listOf(UUID.randomUUID().toString()))
			.assertSuccess()
			.body
		assertEquals(1, filesUnknownAttachment.size)
		assertEquals(SoknadFile.FileStatus.notfound, filesUnknownAttachment[0].fileStatus)
	}


	@Test
	fun `Test parallell delete and sendIn operation on application`() = runBlocking {
		val (skjemaDto, attachments) = createApplication()
		assertTrue( skjemaDto != null)

		val threads = 2
		val callResponses = mutableMapOf<String, HttpStatusCode>()

		val jobs = (1..threads).map { iterasjon ->
			async(Dispatchers.IO) {
				when (iterasjon) {
					1 -> callResponses.put("submitDigitalApplication", api.submitDigitalApplication(skjemaDto, attachments).statusCode)
					2 -> {
						callResponses.put("deleteSoknad", api.deleteSoknad(skjemaDto.innsendingsId!!)?.statusCode ?: HttpStatusCode.valueOf(400))
					}
				}
			}
		}
		val responses = jobs.awaitAll()

		assertTrue(callResponses.count() == 2)
		assertEquals(HttpStatusCode.valueOf(200),callResponses.get("deleteSoknad"))
		assertTrue(HttpStatusCode.valueOf(404)==callResponses.get("submitDigitalApplication") || HttpStatusCode.valueOf(500)==callResponses.get("submitDigitalApplication"))

	}


	private fun createApplication(): Pair<SkjemaDto?, List<AttachmentDto>> {
		val skjemanr = "NAV 10-07.54"
		val skjematittel = "Søknad om servicehund"
		val hoveddokument =
			SkjemaDokumentDtoTestBuilder(tittel = skjematittel).asHovedDokument(skjemanr, withFile = false).build()
		val hoveddokumentVariant =
			SkjemaDokumentDtoTestBuilder(tittel = skjematittel).asHovedDokumentVariant(skjemanr, withFile = true).build()

		val skjemaDto = SkjemaDtoTestBuilder(skjemanr = skjemanr, tittel = skjematittel)
			.medHoveddokument(hoveddokument)
			.medHoveddokumentVariant(hoveddokumentVariant)
			.build()

		val soknad = api.createSoknad(skjemaDto)
			.assertSuccess()
			.body
		val innsendingsId = soknad.innsendingsId!!

		val fileM2part1 = api.uploadAttachmentFile(innsendingsId, "M2")
			.assertSuccess()
			.body
		val fileM2part2 = api.uploadAttachmentFile(innsendingsId, "M2")
			.assertSuccess()
			.body

		val fileM3 = api.uploadAttachmentFile(innsendingsId, "M3")
			.assertSuccess()
			.body

		val attachments = listOf(
			AttachmentDto(
				attachmentCode = "M2",
				"Medisinske utgifter",
				OpplastingsStatusDto.LastetOpp,
				fileIds = listOf(fileM2part1.id, fileM2part2.id)
			),
			AttachmentDto(
				attachmentCode = "M3",
				"Legeerklæring",
				OpplastingsStatusDto.LastetOpp,
				fileIds = listOf(fileM3.id)
			),
			AttachmentDto(attachmentCode = "M4", "Kursbevis", OpplastingsStatusDto.SendesAvAndre),
			AttachmentDto(attachmentCode = "M5", "Leiekontrakt", OpplastingsStatusDto.SendSenere),
		)
		return Pair(api.getSoknad(innsendingsId)?.body, attachments)

	}

}
