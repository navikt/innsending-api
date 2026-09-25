package no.nav.soknad.innsending.rest.fyllut

import com.ninjasquad.springmockk.SpykBean
import io.mockk.clearAllMocks
import io.mockk.slot
import io.mockk.verify
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerAPITest
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.service.DocumentService
import no.nav.soknad.innsending.service.config.ConfigDefinition
import no.nav.soknad.innsending.service.config.ConfigService
import no.nav.soknad.innsending.service.fillager.FileStorageNamespace
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.utils.ApiWebClient
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.TokenGenerator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import java.util.*
import kotlin.test.assertEquals

class NologinApplicationRestApiTest : ApplicationTest() {
	@Autowired
	lateinit var configService: ConfigService

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@SpykBean
	lateinit var metrics: InnsenderMetrics

	@SpykBean
	lateinit var soknadsmottaker: MottakerAPITest

	@SpykBean
	lateinit var documentService: DocumentService

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
	fun `delete no-login application removes only files for the requested application`() {
		val innsendingsId = UUID.randomUUID()
		val otherInnsendingsId = UUID.randomUUID()
		val firstFile = api.uploadNologinFileV2(innsendingsId.toString(), "first").assertSuccess().body
		val secondFile = api.uploadNologinFileV2(innsendingsId.toString(), "second").assertSuccess().body
		val otherFile = api.uploadNologinFileV2(otherInnsendingsId.toString(), "other").assertSuccess().body

		api.deleteApplication(innsendingsId.toString(), ApiWebClient.ApplicationType.NOLOGIN)
			.assertSuccess()
			.assertHttpStatus(HttpStatus.NO_CONTENT)
		api.deleteApplication(innsendingsId.toString(), ApiWebClient.ApplicationType.NOLOGIN)
			.assertSuccess()
			.assertHttpStatus(HttpStatus.NO_CONTENT)

		assertNull(documentService.getFile(FileStorageNamespace.NOLOGIN, innsendingsId, firstFile.id))
		assertNull(documentService.getFile(FileStorageNamespace.NOLOGIN, innsendingsId, secondFile.id))
		assertNotNull(documentService.getFile(FileStorageNamespace.NOLOGIN, otherInnsendingsId, otherFile.id))
		verify(exactly = 2) {
			documentService.deleteAttachment(FileStorageNamespace.NOLOGIN, innsendingsId)
		}
	}


	@Test
	fun `skal sende inn soknad og handtere vedlegg med ulike statuser`() {
		val innsendingId = UUID.randomUUID().toString()

		val navId1 = "personal-id"
		val file1 = api.uploadNologinFileV2(vedleggId = navId1, innsendingId = innsendingId)
			.assertSuccess()
			.body

		val navId2 = "e9logo"
		api.uploadNologinFileV2(vedleggId = navId2, innsendingId = innsendingId)
			.assertSuccess()
			.body.let {
				assertNotNull(it.id)
			}

		val navId3 = "dj5jkj"
		val file3 = api.uploadNologinFileV2(vedleggId = navId3, innsendingId = innsendingId)
			.assertSuccess()
			.body

		val navId4 = "dj5jkj-1"
		val file4 = api.uploadNologinFileV2(vedleggId = navId4, innsendingId = innsendingId)
			.assertSuccess()
			.body

		val attachmentLegitimasjon = AttachmentDto(
			attachmentCode = "K2",
			title = "Norsk pass",
			label = "Norsk pass",
			uploadStatus = OpplastingsStatusDto.LastetOpp,
			fileIds = listOf(file1.id),
		)

		val attachmentSomSendesSenere = AttachmentDto(
			attachmentCode = "T4",
			title = "Kursbevis",
			label = "Kursbevis for førstehjelpskurs",
			uploadStatus = OpplastingsStatusDto.SendSenere,
			fileIds = null,
		)

		val attachmentAnnenDokumentasjon1 = AttachmentDto(
			attachmentCode = "N6",
			title = "Annen dokumentasjon",
			label = "Kvittering fra apotek",
			uploadStatus = OpplastingsStatusDto.LastetOpp,
			fileIds = listOf(file3.id),
		)

		val attachmentAnnenDokumentasjon2 = AttachmentDto(
			attachmentCode = "N6",
			title = "Annen dokumentasjon",
			label = "Førerkort",
			uploadStatus = OpplastingsStatusDto.LastetOpp,
			fileIds = listOf(file4.id),
		)

		val submitResponse = api.submitNologinApplication(
			innsendingId,
			attachments = listOf(
				attachmentLegitimasjon,
				attachmentSomSendesSenere,
				attachmentAnnenDokumentasjon1,
				attachmentAnnenDokumentasjon2
			)
		)
			.assertSuccess()
			.body
		assertEquals(submitResponse.mainDocumentFileId, null, "Skal ikke returnere hoveddokumentRef ved nologin")
		assertEquals(1, submitResponse.attachments?.filter { it.uploadStatus == OpplastingsStatusDto.SendSenere }?.size)
		assertEquals(3, submitResponse.attachments?.filter { it.uploadStatus == OpplastingsStatusDto.Innsendt }?.size)
		assertEquals(0, submitResponse.attachments?.filter { it.uploadStatus == OpplastingsStatusDto.SendesAvAndre }?.size)

		val vedleggT4Ettersending = submitResponse.attachments?.firstOrNull { it.attachmentCode == "T4" }
		assertEquals("Kursbevis for førstehjelpskurs", vedleggT4Ettersending?.label)
		assertEquals(OpplastingsStatusDto.SendSenere, vedleggT4Ettersending?.uploadStatus)

		val vedleggForerkort = submitResponse.attachments?.firstOrNull { it.label == "Førerkort" }
		assertNotNull(vedleggForerkort)
		assertEquals(OpplastingsStatusDto.Innsendt, vedleggForerkort.uploadStatus)

		val vedleggKvittering = submitResponse.attachments?.firstOrNull { it.label == "Kvittering fra apotek" }
		assertNotNull(vedleggKvittering)
		assertEquals(OpplastingsStatusDto.Innsendt, vedleggKvittering.uploadStatus)

		val slotSoknad = slot<DokumentSoknadDto>()
		val slotVedleggsliste = slot<List<VedleggDto>>()
		val slotAvsender = slot<AvsenderDto>()
		val slotBruker = slot<BrukerDto?>()
		verify(timeout = 5000, exactly = 1) {
			soknadsmottaker.sendInnSoknad(
				capture(slotSoknad),
				capture(slotVedleggsliste),
				capture(slotAvsender),
				captureNullable(slotBruker)
			)
		}

		assertEquals(innsendingId, slotSoknad.captured.innsendingsId)
		val innsendteDokumenter = slotVedleggsliste.captured
		assertEquals(5, innsendteDokumenter.size)

		val innsendtK2 = innsendteDokumenter.firstOrNull { it.vedleggsnr == attachmentLegitimasjon.attachmentCode }
		assertNotNull(innsendtK2)

		val vedleggT4 = innsendteDokumenter.firstOrNull { it.vedleggsnr == attachmentSomSendesSenere.attachmentCode }
		assertNull(vedleggT4)

		val innsendtN6_1 =
			innsendteDokumenter.firstOrNull { it.vedleggsnr == attachmentAnnenDokumentasjon1.attachmentCode && it.tittel == attachmentAnnenDokumentasjon1.title }
		assertNotNull(innsendtN6_1)

		val innsendtN6_2 =
			innsendteDokumenter.firstOrNull { it.vedleggsnr == attachmentAnnenDokumentasjon2.attachmentCode && it.tittel == attachmentAnnenDokumentasjon2.title }
		assertNotNull(innsendtN6_2)

		val hoveddokumentListe = innsendteDokumenter.filter { it.erHoveddokument }
		assertEquals(2, hoveddokumentListe.size)
	}


	@Test
	fun `Skal få feilmelding ved forsøk på å sende inn søknad på nytt`() {
		val innsendingId = UUID.randomUUID().toString()

		val navId1 = "personal-id"
		val file1 = api.uploadNologinFileV2(vedleggId = navId1, innsendingId = innsendingId)
			.assertSuccess()
			.body

		val navId2 = "e9logo"
		api.uploadNologinFileV2(vedleggId = navId2, innsendingId = innsendingId)
			.assertSuccess()
			.body.let {
				assertNotNull(it.id)
			}

		val navId3 = "dj5jkj"
		val file3 = api.uploadNologinFileV2(vedleggId = navId3, innsendingId = innsendingId)
			.assertSuccess()
			.body

		val navId4 = "dj5jkj-1"
		val file4 = api.uploadNologinFileV2(vedleggId = navId4, innsendingId = innsendingId)
			.assertSuccess()
			.body

		val attachmentLegitimasjon = AttachmentDto(
			attachmentCode = "K2",
			title = "Norsk pass",
			label = "Norsk pass",
			uploadStatus = OpplastingsStatusDto.LastetOpp,
			fileIds = listOf(file1.id),
		)

		val attachmentSomSendesSenere = AttachmentDto(
			attachmentCode = "T4",
			title = "Kursbevis",
			label = "Kursbevis for førstehjelpskurs",
			uploadStatus = OpplastingsStatusDto.SendSenere,
			fileIds = null,
		)

		val attachmentAnnenDokumentasjon1 = AttachmentDto(
			attachmentCode = "N6",
			title = "Annen dokumentasjon",
			label = "Kvittering fra apotek",
			uploadStatus = OpplastingsStatusDto.LastetOpp,
			fileIds = listOf(file3.id),
		)

		val attachmentAnnenDokumentasjon2 = AttachmentDto(
			attachmentCode = "N6",
			title = "Annen dokumentasjon",
			label = "Førerkort",
			uploadStatus = OpplastingsStatusDto.LastetOpp,
			fileIds = listOf(file4.id),
		)

		val submitResponse = api.submitNologinApplication(
			innsendingId,
			attachments = listOf(
				attachmentLegitimasjon,
				attachmentSomSendesSenere,
				attachmentAnnenDokumentasjon1,
				attachmentAnnenDokumentasjon2
			)
		)
			.assertSuccess()
			.body

		assertEquals(submitResponse.mainDocumentFileId, null, "Skal ikke returnere hoveddokumentRef ved nologin")
		assertEquals(1, submitResponse.attachments?.filter { it.uploadStatus == OpplastingsStatusDto.SendSenere }?.size)
		assertEquals(3, submitResponse.attachments?.filter { it.uploadStatus == OpplastingsStatusDto.Innsendt }?.size)
		assertEquals(0, submitResponse.attachments?.filter { it.uploadStatus == OpplastingsStatusDto.SendesAvAndre }?.size)

		val vedleggT4Ettersending = submitResponse.attachments?.firstOrNull { it.attachmentCode == "T4" }
		assertEquals("Kursbevis for førstehjelpskurs", vedleggT4Ettersending?.label)
		assertEquals(OpplastingsStatusDto.SendSenere, vedleggT4Ettersending?.uploadStatus)

		val vedleggForerkort = submitResponse.attachments?.firstOrNull { it.label == "Førerkort" }
		assertNotNull(vedleggForerkort)
		assertEquals(OpplastingsStatusDto.Innsendt, vedleggForerkort.uploadStatus)

		val vedleggKvittering = submitResponse.attachments?.firstOrNull { it.label == "Kvittering fra apotek" }
		assertNotNull(vedleggKvittering)
		assertEquals(OpplastingsStatusDto.Innsendt, vedleggKvittering.uploadStatus)


		val submitResponse2 = api.submitNologinApplication(
			innsendingId,
			attachments = listOf(
				attachmentLegitimasjon,
				attachmentSomSendesSenere,
				attachmentAnnenDokumentasjon1,
				attachmentAnnenDokumentasjon2
			)
		)
			.assertClientError()
			.errorBody.let {
				assertEquals("Søknad med innsendingsId $innsendingId finnes allerede", it.message)
			}
	}

		@Test
	fun `skal sanitere vedleggstittel og -label`() {
		val innsendingId = UUID.randomUUID().toString()

		val navId3 = "dj5jkj"
		val file3 = api.uploadNologinFileV2(vedleggId = navId3, innsendingId = innsendingId)
			.assertSuccess()
			.body

		val attachmentAnnenDokumentasjon = AttachmentDto(
			attachmentCode = "N6",
			title = '\u007F' + "Annen dokumentasjon" + '\u0000',
			label = '\u007F' + "Kvittering fra apotek" + '\u0000',
			uploadStatus = OpplastingsStatusDto.LastetOpp,
			fileIds = listOf(file3.id),
		)

		val submitResponse = api.submitNologinApplication(
			innsendingId,
			attachments = listOf(attachmentAnnenDokumentasjon)
		)
			.assertSuccess()
			.body

		val vedleggKvittering = submitResponse.attachments?.firstOrNull { it.attachmentCode == attachmentAnnenDokumentasjon.attachmentCode }
		assertNotNull(vedleggKvittering)
		assertEquals(OpplastingsStatusDto.Innsendt, vedleggKvittering.uploadStatus)
		assertEquals("Kvittering fra apotek", vedleggKvittering.label)

		val slotSoknad = slot<DokumentSoknadDto>()
		val slotVedleggsliste = slot<List<VedleggDto>>()
		val slotAvsender = slot<AvsenderDto>()
		val slotBruker = slot<BrukerDto?>()
		verify(timeout = 5000, exactly = 1) {
			soknadsmottaker.sendInnSoknad(
				capture(slotSoknad),
				capture(slotVedleggsliste),
				capture(slotAvsender),
				captureNullable(slotBruker)
			)
		}

		assertEquals(innsendingId, slotSoknad.captured.innsendingsId)
		val innsendteDokumenter = slotVedleggsliste.captured
		assertEquals(3, innsendteDokumenter.size)

		val innsendtN6 =
			innsendteDokumenter.firstOrNull { it.vedleggsnr == attachmentAnnenDokumentasjon.attachmentCode }
		assertNotNull(innsendtN6)
		assertEquals("Annen dokumentasjon", innsendtN6.tittel)
		assertEquals("Kvittering fra apotek", innsendtN6.label)

		val hoveddokumentListe = innsendteDokumenter.filter { it.erHoveddokument }
		assertEquals(2, hoveddokumentListe.size)
	}




	@Test
	fun `skal avvise innsending dersom soknad allerede er sendt inn`() {
		val innsendingsId = UUID.randomUUID().toString()

		api.submitNologinApplication(
			innsendingsId = innsendingsId,
			formNumber = "NAV 11-12.12",
			title = "Testskjema",
		)
			.assertSuccess()

		api.submitNologinApplication(
			innsendingsId = innsendingsId,
			formNumber = "NAV 11-12.12",
			title = "Testskjema",
		)
			.assertClientError()
			.errorBody.let {
				assertEquals("Søknad med innsendingsId $innsendingsId finnes allerede", it.message)
			}
	}


	@Test
	fun `skal avvise innsending dersom nologin main switch er av`() {
		configService.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "off", "test")
		api.submitNologinApplication(
			innsendingsId = UUID.randomUUID().toString(),
			formNumber = "NAV 11-12.12",
			title = "Testskjema",
		)
			.assertHttpStatus(HttpStatus.SERVICE_UNAVAILABLE)
			.errorBody.let { body ->
				assertEquals("temporarilyUnavailable", body.errorCode)
			}
	}


	@Test
	fun `skal sende inn søknad uten brukerId`() {
		val innsendingsId = UUID.randomUUID().toString()
		val avsender = AvsenderDto(
			id = "123456789",
			idType = AvsenderDto.IdType.ORGNR,
			navn = "Are Avsender AS",
		)

		api.submitNologinApplication(
			innsendingsId = innsendingsId,
			brukerId = null,
			avsender = avsender,
		)
			.assertSuccess()

		val slotSoknad = slot<DokumentSoknadDto>()
		val slotVedleggsliste = slot<List<VedleggDto>>()
		val slotAvsender = slot<AvsenderDto>()
		val slotBruker = slot<BrukerDto?>()
		verify(timeout = 5000, exactly = 1) {
			soknadsmottaker.sendInnSoknad(
				capture(slotSoknad),
				capture(slotVedleggsliste),
				capture(slotAvsender),
				captureNullable(slotBruker)
			)
		}
		assertNull(slotBruker.captured)
		val actualAvsender = slotAvsender.captured
		assertNotNull(actualAvsender)
		assertEquals(avsender.id, actualAvsender.id)
		assertEquals(avsender.idType, actualAvsender.idType)
		assertEquals(avsender.navn, actualAvsender.navn)
	}

	@Test
	fun `skal avvise innsending med ugyldig avsenderid i SubmitApplicationRequest`() {
		api.submitNologinApplication(
			innsendingsId = UUID.randomUUID().toString(),
			brukerId = null,
			avsender = AvsenderDto(
				id = "1234 56789",
				idType = AvsenderDto.IdType.ORGNR,
				navn = "Are Avsender AS",
			),
		)
			.assertHttpStatus(HttpStatus.BAD_REQUEST)
			.assertErrorCode(ErrorCode.ILLEGAL_ARGUMENT)
			.errorBody.let {
				assertEquals("avsender.id kan ikke inneholde mellomrom", it.message)
			}
	}


	@Test
	fun `innsending skal feile dersom hverken avsender eller bruker er satt`() {
		api.submitNologinApplication(
			innsendingsId = UUID.randomUUID().toString(),
			brukerId = null,
			avsender = null,
		)
			.assertClientError()
			.errorBody.let {
				assertEquals("Hverken bruker eller avsender er satt", it.message)
			}
	}

}
