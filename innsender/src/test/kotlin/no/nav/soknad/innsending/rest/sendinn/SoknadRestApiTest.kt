package no.nav.soknad.innsending.rest.sendinn

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkSpyBean
import io.mockk.clearAllMocks
import io.mockk.justRun
import io.mockk.slot
import io.mockk.verify
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerAPITest
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.model.AvsenderDto
import no.nav.soknad.innsending.model.BrukerDto
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.EnvQualifier
import no.nav.soknad.innsending.model.Mimetype
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.PatchVedleggDto
import no.nav.soknad.innsending.model.SoknadType
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.repository.domain.enums.OpplastingsStatus
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.ApplicationOwner
import no.nav.soknad.innsending.repository.domain.models.FilDbData
import no.nav.soknad.innsending.repository.domain.models.VedleggDbData
import no.nav.soknad.innsending.service.RepositoryUtils
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.utils.ApiWebClient
import no.nav.soknad.innsending.utils.ApplicationTestRepository
import no.nav.soknad.innsending.utils.TokenGenerator
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.awaitNotification
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.OpprettEttersendingBuilder
import no.nav.soknad.innsending.utils.builders.SkjemaDokumentDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.SkjemaDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.SoknadDbDataTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.awaitility.Awaitility.await
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.context.annotation.Import
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertNotEquals

@Import(ApplicationTestRepository::class)
class SoknadRestApiTest : ApplicationTest() {
	@Autowired
	lateinit var storedApplications: ApplicationTestRepository

	@Autowired
	lateinit var repoUtils: RepositoryUtils

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var soknadService: SoknadService

	@MockkSpyBean
	lateinit var notificationPublisher: PublisherInterface

	@MockkBean
	private lateinit var soknadsmottakerAPI: MottakerAPITest

	@LocalServerPort
	var serverPort: Int = 0

	private val defaultUser = "12345678901"
	private val defaultSkjemanr = "NAV 55-00.60"

	var testApi: ApiWebClient? = null
	val api: ApiWebClient
		get() = testApi!!

	@BeforeEach
	fun setup() {
		testApi = ApiWebClient(webTestClient, serverPort, mockOAuth2Server)
		clearAllMocks()
	}

	companion object {
		@JvmStatic
		fun hentSoknaderForSkjemanr() = listOf(
			Arguments.of(null, 3, 2, 1), // None, should return all (2 ettersendelse and 1 søknad)
			Arguments.of("ettersendelse", 2, 2, 0), // Ettersendelse, should return 2 ettersendelser
			Arguments.of("soknad", 1, 0, 1), // Søknad, should return 1 søknad
			Arguments.of("soknad,ettersendelse", 3, 2, 1) // Both, should return all (2 ettersendelse and 1 søknad)
		)
	}

	@ParameterizedTest
	@MethodSource("hentSoknaderForSkjemanr")
	fun `Should return response based on soknadstype query param`(
		queryParam: String?,
		expectedTotalSize: Int,
		expectedEttersendingSize: Int,
		expectedSoknadSize: Int
	) {
		// Given
		// 1 søknad and 2 ettersendingssøknader
		val soknad = DokumentSoknadDtoTestBuilder(brukerId = defaultUser).build()
		val opprettetSoknad = soknadService.opprettNySoknad(soknad)

		val ettersending =
			DokumentSoknadDtoTestBuilder(skjemanr = opprettetSoknad.skjemanr, brukerId = defaultUser).asEttersending().build()
		soknadService.opprettNySoknad(ettersending)

		val ettersending2 =
			DokumentSoknadDtoTestBuilder(skjemanr = opprettetSoknad.skjemanr, brukerId = defaultUser).asEttersending().build()
		soknadService.opprettNySoknad(ettersending2)

		val response = api.getExistingSoknader(opprettetSoknad.skjemanr, queryParam)

		// Then
		val body = response?.body!!
		val responseEttersending = body.filter { it.soknadstype == SoknadType.ettersendelse }
		val responseSoknad = body.filter { it.soknadstype == SoknadType.soknad }

		assertEquals(HttpStatus.OK, response.statusCode)
		assertTrue(response.body != null)
		assertEquals(expectedTotalSize, response.body!!.size)
		assertEquals(expectedEttersendingSize, responseEttersending.size)
		assertEquals(expectedSoknadSize, responseSoknad.size)
	}

	@Test
	fun `(Temporary bugfix) Should fix attachment status before submit`() {
		val soknad = SoknadDbDataTestBuilder(ettersendingsId = UUID.randomUUID().toString(), forsteinnsendingsdato = LocalDateTime.now()).build()
		repoUtils.lagreSoknad(soknad)
		val vedlegg = VedleggDbData(
			id = null,
			soknadsid = soknad.id!!,
			vedleggsnr = "W7",
			status = OpplastingsStatus.LASTET_OPP,
			erhoveddokument = false,
			ervariant = false,
			erpdfa = false,
			erpakrevd = false,
			tittel = "test",
			label = "test",
			beskrivelse = "test",
			mimetype = "application/pdf",
			uuid = UUID.randomUUID().toString(),
			opprettetdato = LocalDateTime.now(),
			endretdato = LocalDateTime.now(),
			innsendtdato = null,
			vedleggsurl = null,
			formioid = null,
			opplastingsvalgkommentarledetekst = null,
			opplastingsvalgkommentar = null,
			fileIds = null,
		)
		repoUtils.lagreVedlegg(
			vedlegg
		)
		val data = Hjelpemetoder.getBytesFromFile("/litenPdf.pdf")
		repoUtils.saveFilDbData(soknad.innsendingsid, FilDbData(
			id = null,
			vedleggsid = vedlegg.id!!,
			filnavn = "Test.pdf",
			mimetype = "application/pdf",
			storrelse = data.size,
			data = data,
			opprettetdato = LocalDateTime.now(),
			antallsider = 1,
		))
		repoUtils.lagreVedlegg(
			VedleggDbData(
				id = null,
				soknadsid = soknad.id!!,
				vedleggsnr = "W6",
				status = OpplastingsStatus.KLAR_FOR_INNSENDING, // <- this status need to be replaced before submit
				erhoveddokument = false,
				ervariant = false,
				erpdfa = false,
				erpakrevd = false,
				tittel = "test",
				label = "test",
				beskrivelse = "test",
				mimetype = "application/pdf",
				uuid = UUID.randomUUID().toString(),
				opprettetdato = LocalDateTime.now(),
				endretdato = LocalDateTime.now(),
				innsendtdato = null,
				vedleggsurl = null,
				formioid = null,
				opplastingsvalgkommentarledetekst = null,
				opplastingsvalgkommentar = null,
				fileIds = null,
			)
		)

		api.sendInnSoknad(soknad.innsendingsid).assertSuccess()

		// verify invocation of soknadsmottaker
		val slotSoknad = slot<DokumentSoknadDto>()
		val slotVedleggsliste = slot<List<VedleggDto>>()
		val slotAvsender = slot<AvsenderDto>()
		val slotBruker = slot<BrukerDto?>()
		verify(timeout = 1000, exactly = 1) {
			soknadsmottakerAPI.sendInnSoknad(
				capture(slotSoknad),
				capture(slotVedleggsliste),
				capture(slotAvsender),
				captureNullable(slotBruker)
			)
		}

		assertTrue(slotVedleggsliste.captured.none { v -> v.vedleggsnr == "W6" })
		assertEquals(3, slotVedleggsliste.captured.size)
	}

	@Test
	fun `Should create notification when ettersending is automatically created due to missing attachments`() {
		val createSoknadRequest = SkjemaDtoTestBuilder(brukerId = "10987654321", vedleggsListe = listOf(
			SkjemaDokumentDtoTestBuilder(vedleggsnr = "T7").build(),
			SkjemaDokumentDtoTestBuilder(vedleggsnr = "N6").build()
		)).build()
		val innsendingsId = api.createSoknad(createSoknadRequest, envQualifier = EnvQualifier.preprodAnsatt)
			.assertSuccess().body.innsendingsId!!
		notificationPublisher.awaitNotification(innsendingsId)
		val soknad = api.getSoknadSendinn(innsendingsId)
			.assertSuccess().body

		val vedleggsId = soknad.vedleggsListe.first { it.vedleggsnr == "N6" }.id
		val fil = Hjelpemetoder.getBytesFromFile("/litenPdf.pdf")
		api.uploadFile(innsendingsId, vedleggsId!!, fil)
		// REST cannot assign a historical owner from the token's PDL history.
		val historicalOwner = "12345678902"
		val owner = ApplicationOwner.person(historicalOwner)
		storedApplications.setOwner(innsendingsId, owner)
		justRun { soknadsmottakerAPI.sendInnSoknad(match { it.innsendingsId == innsendingsId }, any(), any(), any(), any()) }

		val innsendingskvittering = api.sendInnSoknad(innsendingsId, EnvQualifier.preprodAnsatt)
			.assertSuccess().body
		assertEquals(1, innsendingskvittering.skalEttersendes?.size)

		val notifications = mutableListOf<AddNotification>()
		verify(timeout = 5000, exactly = 2) {
			notificationPublisher.opprettBrukernotifikasjon(match {
				it.soknadRef.groupId == innsendingsId
			})
		}
		verify { notificationPublisher.opprettBrukernotifikasjon(capture(notifications)) }
		val groupNotifications = notifications.filter { it.soknadRef.groupId == innsendingsId }
		verify(timeout = 5000, exactly = 1) {
			notificationPublisher.avsluttBrukernotifikasjon(match {
				it.innsendingId == innsendingsId && it.groupId == innsendingsId && it.personId == historicalOwner
			})
		}

		val notificationForInitialSoknad = groupNotifications.single { it.soknadRef.innsendingId == innsendingsId }
		assertTrue(notificationForInitialSoknad.soknadRef.personId == TokenGenerator.subject, "Creation must notify the original owner")
		assertFalse(notificationForInitialSoknad.soknadRef.erEttersendelse)
		assertEquals(false, notificationForInitialSoknad.soknadRef.erSystemGenerert)
		assertEquals(innsendingsId, notificationForInitialSoknad.soknadRef.innsendingId)
		assertEquals(innsendingsId, notificationForInitialSoknad.soknadRef.groupId)
		assertNull(notificationForInitialSoknad.brukernotifikasjonInfo.utsettSendingTil)
		assertTrue(
			notificationForInitialSoknad.brukernotifikasjonInfo.lenke.contains("ansatt.dev.nav.no"),
			"Unexpected link: ${notificationForInitialSoknad.brukernotifikasjonInfo.lenke}"
		)

		val notificationForEttersending = groupNotifications.single { it.soknadRef.innsendingId != innsendingsId }
		assertTrue(notificationForEttersending.soknadRef.personId == historicalOwner, "The successor must notify the inherited owner")
		assertTrue(notificationForEttersending.soknadRef.erEttersendelse)
		assertEquals(true, notificationForEttersending.soknadRef.erSystemGenerert)
		assertNotNull(notificationForEttersending.soknadRef.innsendingId)
		assertNotEquals(innsendingsId, notificationForEttersending.soknadRef.innsendingId)
		assertEquals(innsendingsId, notificationForEttersending.soknadRef.groupId)
		assertNotNull(notificationForEttersending.brukernotifikasjonInfo.utsettSendingTil)
		assertTrue(
			notificationForEttersending.brukernotifikasjonInfo.lenke.contains("ansatt.dev.nav.no"),
			"Unexpected link: ${notificationForEttersending.brukernotifikasjonInfo.lenke}"
		)
		val successorId = notificationForEttersending.soknadRef.innsendingId
		val successor = api.getSoknadSendinn(successorId).assertHttpStatus(HttpStatus.OK).body
		assertEquals(innsendingsId, successor.ettersendingsId)
		assertEquals(SoknadsStatusDto.Opprettet, successor.status)
		assertTrue(successor.brukerId == TokenGenerator.subject, "The successor must retain the legacy user identity")
		assertEquals(createSoknadRequest.tittel, successor.tittel)
		assertEquals(createSoknadRequest.skjemanr, successor.skjemanr)
		val missingAttachment = successor.vedleggsListe.single { it.vedleggsnr == "T7" }
		assertEquals("T7", missingAttachment.vedleggsnr)
		assertEquals(OpplastingsStatusDto.IkkeValgt, missingAttachment.opplastingsStatus)
		assertEquals(true, missingAttachment.erPakrevd)
		assertFalse(missingAttachment.erHoveddokument)
		assertTrue(missingAttachment.fileIds.isNullOrEmpty())

		val submittedAttachments = slot<List<VedleggDto>>()
		verify(timeout = 5000, exactly = 1) {
			soknadsmottakerAPI.sendInnSoknad(
				match { it.innsendingsId == innsendingsId && it.brukerId == TokenGenerator.subject },
				capture(submittedAttachments),
				eq(AvsenderDto(id = TokenGenerator.subject, idType = AvsenderDto.IdType.FNR)),
				eq(BrukerDto(id = TokenGenerator.subject, idType = BrukerDto.IdType.FNR)),
				isNull(),
			)
		}
		assertTrue(submittedAttachments.captured.none { it.vedleggsnr == "T7" })
		val submittedMainDocuments = submittedAttachments.captured.filter { it.erHoveddokument }
		assertEquals(2, submittedMainDocuments.size)
		assertEquals(setOf(false, true), submittedMainDocuments.map { it.erVariant }.toSet())
		assertEquals(setOf(Mimetype.applicationSlashPdf, Mimetype.applicationSlashJson), submittedMainDocuments.map { it.mimetype }.toSet())
		assertEquals(Mimetype.applicationSlashPdf, submittedAttachments.captured.single { it.vedleggsnr == Constants.KVITTERINGS_NR }.mimetype)
		assertTrue(submittedAttachments.captured.all { it.opplastingsStatus == OpplastingsStatusDto.KlarForInnsending })
		// The submitted original has no readable person GET. Await its commit before checking hidden owners and final call counts.
		await().atMost(Duration.ofSeconds(5)).untilAsserted {
			val original = storedApplications.findBySubmissionId(innsendingsId)
			assertEquals(SoknadsStatus.Innsendt, original.status)
			listOf(original, storedApplications.findBySubmissionId(successorId)).forEach {
				assertEquals(false, it.ownerIsSqlNull)
				assertTrue(it.owner == owner, "Original and automatic successor must retain the stored owner")
			}
		}
		val ids = setOf(innsendingsId, successorId)
		verify(exactly = 1) { soknadsmottakerAPI.sendInnSoknad(match { it.innsendingsId in ids }, any(), any(), any(), any()) }
		verify(exactly = 2) { notificationPublisher.opprettBrukernotifikasjon(match { it.soknadRef.innsendingId in ids }) }
		verify(exactly = 1) { notificationPublisher.avsluttBrukernotifikasjon(match { it.innsendingId in ids }) }
		val activeGroup = api.getExistingSoknader(createSoknadRequest.skjemanr).assertHttpStatus(HttpStatus.OK).body
			.filter { it.innsendingsId == innsendingsId || it.ettersendingsId == innsendingsId }
		assertEquals(listOf(successorId), activeGroup.map { it.innsendingsId })
		api.getSoknadSendinn(innsendingsId).assertHttpStatus(HttpStatus.BAD_REQUEST)
			.assertErrorCode(ErrorCode.APPLICATION_SENT_IN_OR_DELETED)
	}


	@Test
	fun `Should reject submission if ettersending has no attachments`() {
		val createEttersending = OpprettEttersendingBuilder().build()
		val innsendingsId = api.createEttersending(createEttersending, envQualifier = EnvQualifier.preprodAnsatt)
			.assertSuccess().body.innsendingsId!!

		api.sendInnSoknad(innsendingsId, EnvQualifier.preprodAnsatt)
			.assertClientError()
	}

	@Test
	fun `Should reject submission if ettersending only has postponded uploads to attachments`() {
		val createEttersending = OpprettEttersendingBuilder().medVedleggGittNr(listOf("W1", "W2")).build()
		val soknad = api.createEttersending(createEttersending, envQualifier = EnvQualifier.preprodAnsatt)
			.assertSuccess().body

		api.sendInnSoknad(soknad.innsendingsId!!, EnvQualifier.preprodAnsatt)
			.assertClientError()
	}

	@Test
	fun `Should accept submission if ettersending has postponded uploads, and changed uploadStatus to attachments`() {
		val createEttersending = OpprettEttersendingBuilder().medVedleggGittNr(listOf("W1", "W2")).build()
		val soknad = api.createEttersending(createEttersending, envQualifier = EnvQualifier.preprodAnsatt)
			.assertSuccess().body
		val innsendingsId = soknad.innsendingsId!!
		val vedleggsId = soknad.vedleggsListe.first { it.vedleggsnr == "W1" }.id!!
		api.patchVedlegg(innsendingsId = innsendingsId, vedleggsId = vedleggsId, PatchVedleggDto(opplastingsStatus = OpplastingsStatusDto.SendesAvAndre)).assertSuccess()

		val innsendingskvittering = api.sendInnSoknad(innsendingsId, EnvQualifier.preprodAnsatt)
			.assertSuccess().body

		assertEquals(1, innsendingskvittering.skalEttersendes?.size)
		assertEquals(1, innsendingskvittering.skalSendesAvAndre?.size)
	}

}
