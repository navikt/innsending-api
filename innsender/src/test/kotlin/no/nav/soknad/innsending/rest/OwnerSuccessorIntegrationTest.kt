package no.nav.soknad.innsending.rest

import com.ninjasquad.springmockk.MockkSpyBean
import io.mockk.clearMocks
import io.mockk.slot
import io.mockk.verify
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerAPITest
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.model.AvsenderDto
import no.nav.soknad.innsending.model.BrukerDto
import no.nav.soknad.innsending.model.Mimetype
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.PatchVedleggDto
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.ApplicationOwner
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.utils.ApiWebClient
import no.nav.soknad.innsending.utils.ApplicationTestRepository
import no.nav.soknad.innsending.utils.TokenGenerator
import no.nav.soknad.innsending.utils.awaitNotification
import no.nav.soknad.innsending.utils.builders.SkjemaDokumentDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.SkjemaDtoTestBuilder
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.util.AopTestUtils
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Import(ApplicationTestRepository::class)
class OwnerSuccessorIntegrationTest : ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var applicationTestRepository: ApplicationTestRepository

	@MockkSpyBean
	lateinit var notificationPublisher: PublisherInterface

	@MockkSpyBean
	lateinit var submissionReceiver: MottakerAPITest

	@LocalServerPort
	var serverPort: Int = 0

	private lateinit var api: ApiWebClient
	private val publisherTarget: PublisherInterface
		get() = AopTestUtils.getUltimateTargetObject(notificationPublisher)
	private val receiverTarget: MottakerAPITest
		get() = AopTestUtils.getUltimateTargetObject(submissionReceiver)

	@BeforeEach
	fun setup() {
		api = ApiWebClient(webTestClient, serverPort, mockOAuth2Server)
		clearMocks(publisherTarget, receiverTarget, answers = false)
	}

	@ParameterizedTest(name = "source has historical owner: {0}")
	@ValueSource(booleans = [true, false])
	fun `automatic successor inherits the stored owner without changing legacy notification recipients`(hasHistoricalOwner: Boolean) {
		val historicalOwner = "12345678902"
		val requestIdentity = "12128012345"
		val sender = AvsenderDto(id = TokenGenerator.subject, idType = AvsenderDto.IdType.FNR)
		val expectedUser = BrukerDto(id = TokenGenerator.subject, idType = BrukerDto.IdType.FNR)
		val formNumber = "NAV 10-07.54"
		val title = "Automatic successor owner test"
		val missingAttachment = SkjemaDokumentDtoTestBuilder(
			vedleggsnr = "M5",
			tittel = "Required supporting document",
			label = "Required supporting document",
			beskrivelse = "Required documentation for this application",
			pakrevd = true,
		).build()
		val request = SkjemaDtoTestBuilder(brukerId = requestIdentity, skjemanr = formNumber, tittel = title, tema = "BIL")
			.medHoveddokument(SkjemaDokumentDtoTestBuilder(tittel = title).asHovedDokument(formNumber, withFile = false).build())
			.build()
		val created = api.createSoknad(request).assertHttpStatus(HttpStatus.CREATED).body
		val applicationId = assertNotNull(created.innsendingsId)
		assertTrue(created.brukerId == TokenGenerator.subject, "Creation must ignore the request identity")
		val originalNotification = publisherTarget.awaitNotification(applicationId)

		val completed = api.utfyltSoknad(applicationId, request.copy(vedleggsListe = listOf(missingAttachment)))
		assertEquals(HttpStatus.FOUND, completed.statusCode)
		assertNotNull(completed.headers.location)
		assertNull(completed.body)
		val prepared = api.getSoknadSendinn(applicationId).assertHttpStatus(HttpStatus.OK).body
		assertEquals(SoknadsStatusDto.Utfylt, prepared.status)
		val mainDocument = prepared.vedleggsListe.single { it.erHoveddokument && !it.erVariant }
		api.uploadFile(applicationId, assertNotNull(mainDocument.id)).assertHttpStatus(HttpStatus.CREATED)
		val requiredAttachment = prepared.vedleggsListe.single { it.vedleggsnr == missingAttachment.vedleggsnr }
		assertEquals(true, requiredAttachment.erPakrevd)
		val patched = api.patchVedlegg(
			applicationId,
			assertNotNull(requiredAttachment.id),
			PatchVedleggDto(opplastingsStatus = OpplastingsStatusDto.SendSenere),
		).assertHttpStatus(HttpStatus.OK).body
		assertEquals(OpplastingsStatusDto.SendSenere, patched.opplastingsStatus)

		// REST cannot create a historical owner or a pre-release SQL NULL owner.
		val sourceOwner = if (hasHistoricalOwner) ApplicationOwner.person(historicalOwner) else null
		applicationTestRepository.setOwner(applicationId, sourceOwner)

		val receipt = api.sendInnSoknad(applicationId).assertHttpStatus(HttpStatus.OK).body
		assertEquals(applicationId, receipt.innsendingsId)
		assertEquals(title, receipt.label)
		assertNotNull(receipt.mottattdato)
		assertNotNull(receipt.hoveddokumentRef)
		assertNotNull(receipt.ettersendingsfrist)
		val missingFromReceipt = assertNotNull(receipt.skalEttersendes).single()
		assertEquals(missingAttachment.vedleggsnr, missingFromReceipt.vedleggsnr)
		assertEquals(missingAttachment.label, missingFromReceipt.tittel)
		assertTrue(receipt.innsendteVedlegg.isNullOrEmpty())
		assertTrue(receipt.skalSendesAvAndre.isNullOrEmpty())
		// This receipt has no successor ID; discover it through the existing REST listing helper.
		val listedSuccessor = api.getExistingSoknader(formNumber).assertHttpStatus(HttpStatus.OK).body
			.single { it.ettersendingsId == applicationId }
		val successorId = assertNotNull(listedSuccessor.innsendingsId)
		assertNotEquals(applicationId, successorId)

		val successor = api.getSoknadSendinn(successorId).assertHttpStatus(HttpStatus.OK).body
		assertEquals(successorId, successor.innsendingsId)
		assertEquals(applicationId, successor.ettersendingsId)
		assertEquals(SoknadsStatusDto.Opprettet, successor.status)
		assertTrue(successor.brukerId == TokenGenerator.subject, "Successor access must retain the legacy user identity")
		assertEquals(title, successor.tittel)
		assertEquals(formNumber, successor.skjemanr)
		val successorAttachment = successor.vedleggsListe.single()
		assertEquals(missingAttachment.vedleggsnr, successorAttachment.vedleggsnr)
		assertEquals(OpplastingsStatusDto.IkkeValgt, successorAttachment.opplastingsStatus)
		assertEquals(true, successorAttachment.erPakrevd)
		assertFalse(successorAttachment.erHoveddokument)
		assertTrue(successorAttachment.fileIds.isNullOrEmpty())
		assertStoredOwner(applicationId, sourceOwner)
		assertStoredOwner(successorId, sourceOwner)

		val submittedAttachments = slot<List<VedleggDto>>()
		verify(timeout = 5000, exactly = 1) {
			receiverTarget.sendInnSoknad(
				match { it.innsendingsId == applicationId && it.brukerId == TokenGenerator.subject },
				capture(submittedAttachments),
				eq(sender),
				eq(expectedUser),
				isNull(),
			)
		}
		assertEquals(3, submittedAttachments.captured.size)
		val submittedMainDocuments = submittedAttachments.captured.filter { it.erHoveddokument }
		assertEquals(2, submittedMainDocuments.size)
		assertEquals(setOf(false, true), submittedMainDocuments.map { it.erVariant }.toSet())
		assertEquals(
			setOf(Mimetype.applicationSlashPdf, Mimetype.applicationSlashJson),
			submittedMainDocuments.map { it.mimetype }.toSet(),
		)
		val submittedReceipt = submittedAttachments.captured.single { it.vedleggsnr == Constants.KVITTERINGS_NR }
		assertEquals(Mimetype.applicationSlashPdf, submittedReceipt.mimetype)
		assertTrue(submittedAttachments.captured.all { it.opplastingsStatus == OpplastingsStatusDto.KlarForInnsending })
		assertTrue(submittedAttachments.captured.none { it.vedleggsnr == missingAttachment.vedleggsnr })

		val successorNotification = publisherTarget.awaitNotification(successorId)
		verify(timeout = 5000, exactly = 1) {
			publisherTarget.avsluttBrukernotifikasjon(match {
				it.innsendingId == applicationId && it.personId == TokenGenerator.subject && it.groupId == applicationId
			})
		}
		listOf(originalNotification, successorNotification).forEach {
			assertTrue(it.soknadRef.personId == TokenGenerator.subject, "Release one must keep notifications addressed to the legacy user")
			assertEquals(applicationId, it.soknadRef.groupId)
		}
		assertEquals(false, originalNotification.soknadRef.erEttersendelse)
		assertEquals(true, successorNotification.soknadRef.erEttersendelse)
		assertEquals(true, successorNotification.soknadRef.erSystemGenerert)

		// The submitted original has no readable person GET. Await the archive commit before checking call totals.
		await().atMost(Duration.ofSeconds(5)).untilAsserted {
			assertEquals(SoknadsStatus.Innsendt, applicationTestRepository.findBySubmissionId(applicationId).status,
				"The original submission must finish archiving before checking for duplicate side effects")
		}

		val testIds = setOf(applicationId, successorId)
		verify(exactly = 2) {
			publisherTarget.opprettBrukernotifikasjon(match { it.soknadRef.innsendingId in testIds })
		}
		verify(exactly = 1) {
			publisherTarget.avsluttBrukernotifikasjon(match { it.innsendingId in testIds })
		}
		verify(exactly = 1) {
			receiverTarget.sendInnSoknad(match { it.innsendingsId in testIds }, any(), any(), any(), any())
		}
		val activeGroup = api.getExistingSoknader(formNumber).assertHttpStatus(HttpStatus.OK).body
			.filter { it.innsendingsId == applicationId || it.ettersendingsId == applicationId }
		assertEquals(listOf(successorId), activeGroup.map { it.innsendingsId })
		api.getSoknadSendinn(applicationId)
			.assertHttpStatus(HttpStatus.BAD_REQUEST)
			.assertErrorCode(ErrorCode.APPLICATION_SENT_IN_OR_DELETED)
	}

	private fun assertStoredOwner(applicationId: String, expectedOwner: ApplicationOwner?) {
		// Owner is hidden from REST DTOs. SQL distinguishes inherited SQL NULL from a populated JSON owner.
		val stored = applicationTestRepository.findBySubmissionId(applicationId)
		assertEquals(expectedOwner == null, stored.ownerIsSqlNull)
		assertTrue(stored.owner == expectedOwner, "Submission must preserve the source owner, including SQL NULL")
	}
}
