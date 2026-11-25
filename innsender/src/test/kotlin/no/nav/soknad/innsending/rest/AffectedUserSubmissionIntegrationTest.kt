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
import no.nav.soknad.innsending.model.ApplicationSubmissionResponse
import no.nav.soknad.innsending.model.AttachmentDto
import no.nav.soknad.innsending.model.AvsenderDto
import no.nav.soknad.innsending.model.BrukerDto
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.SkjemaDto
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.ApplicationOwner
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.utils.ApiWebClient
import no.nav.soknad.innsending.utils.ApplicationTestRepository
import no.nav.soknad.innsending.utils.TokenGenerator
import no.nav.soknad.innsending.utils.awaitNotification
import no.nav.soknad.innsending.utils.builders.SkjemaDtoTestBuilder
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.util.AopTestUtils
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Import(ApplicationTestRepository::class)
class AffectedUserSubmissionIntegrationTest : ApplicationTest() {

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
	private val self = BrukerDto(id = TokenGenerator.subject, idType = BrukerDto.IdType.FNR)
	private val other = BrukerDto(id = "10987654321", idType = BrukerDto.IdType.FNR)
	private val personSender = AvsenderDto(id = TokenGenerator.subject, idType = AvsenderDto.IdType.FNR)
	private val organizationSender = AvsenderDto(id = "123456789", idType = AvsenderDto.IdType.ORGNR, navn = "Synthetic organization")
	private val receiverTarget: MottakerAPITest
		get() = AopTestUtils.getUltimateTargetObject(submissionReceiver)

	@BeforeEach
	fun setup() {
		api = ApiWebClient(webTestClient, serverPort, mockOAuth2Server)
		clearMocks(notificationPublisher, receiverTarget, answers = false)
	}

	@Test
	fun `identified self is submitted with the explicit request identity`() {
		val created = createApplication()
		val applicationId = assertNotNull(created.innsendingsId)
		// Neither the owner nor the affected party is exposed by the draft REST response.
		val initial = applicationTestRepository.findBySubmissionId(applicationId)
		assertTrue(initial.affectedUser == self, "Creation must initialize the affected party to the authenticated person")
		assertTrue(!initial.affectedUserIsSqlNull)
		assertTrue(initial.owner == ApplicationOwner.person(TokenGenerator.subject))

		val receipt = api.submitDigitalApplication(created, bruker = TokenGenerator.subject)
			.assertHttpStatus(HttpStatus.OK).body

		assertReceipt(created, receipt)
		assertCompletedSubmission(applicationId, self, personSender, null)
	}

	@Test
	fun `identified other person is submitted without changing the owner`() {
		val created = createApplication()
		val applicationId = assertNotNull(created.innsendingsId)

		val receipt = api.submitDigitalApplication(created, bruker = other.id, grantUserDigitalAccess = true)
			.assertHttpStatus(HttpStatus.OK).body

		assertReceipt(created, receipt)
		assertCompletedSubmission(applicationId, other, personSender, true)
	}

	@Test
	fun `unidentified other person with organization sender remains unidentified in a submitted successor`() {
		val created = createApplication()
		val applicationId = assertNotNull(created.innsendingsId)
		val deferred = AttachmentDto(
			attachmentCode = "M5",
			label = "Supporting documentation",
			uploadStatus = OpplastingsStatusDto.SendSenere,
		)

		val receipt = api.submitDigitalApplication(
			created,
			attachments = listOf(deferred),
			avsender = organizationSender,
			grantUserDigitalAccess = true,
			bruker = null,
		).assertHttpStatus(HttpStatus.OK).body

		assertEquals(applicationId, receipt.innsendingsId.toString())
		assertEquals(created.tittel, receipt.title)
		assertNotNull(receipt.submittedAt)
		assertNotNull(receipt.mainDocumentFileId)
		assertEquals(OpplastingsStatusDto.SendSenere, assertNotNull(receipt.attachments).single().uploadStatus)
		val successorId = assertNotNull(receipt.ettersendingsId).toString()
		assertCompletedSubmission(applicationId, null, organizationSender, true)
		val notification = notificationPublisher.awaitNotification(successorId)
		assertTrue(notification.soknadRef.personId == TokenGenerator.subject,
			"Successor notification must address the owner, not the affected party or sender")
		val successor = api.getSoknadSendinn(successorId).assertHttpStatus(HttpStatus.OK).body
		assertEquals(successorId, successor.innsendingsId)
		assertTrue(successor.brukerId == TokenGenerator.subject)
		val attachment = successor.vedleggsListe.single { it.vedleggsnr == deferred.attachmentCode }
		// REST exposes the successor's documents, but not its persisted parties or ownership.
		val storedSuccessor = applicationTestRepository.findBySubmissionId(successorId)
		assertTrue(storedSuccessor.affectedUserIsSqlNull)
		assertNull(storedSuccessor.affectedUser)
		assertTrue(storedSuccessor.sender == organizationSender)
		assertTrue(storedSuccessor.owner == ApplicationOwner.person(TokenGenerator.subject))
		assertEquals(true, storedSuccessor.grantUserDigitalAccess)
		api.uploadFile(successorId, assertNotNull(attachment.id)).assertHttpStatus(HttpStatus.CREATED)
		val uploaded = api.getSoknadSendinn(successorId).assertHttpStatus(HttpStatus.OK).body
		assertEquals(OpplastingsStatusDto.LastetOpp,
			uploaded.vedleggsListe.single { it.vedleggsnr == deferred.attachmentCode }.opplastingsStatus)

		val successorReceipt = api.sendInnSoknad(successorId).assertHttpStatus(HttpStatus.OK).body

		assertEquals(successorId, successorReceipt.innsendingsId)
		assertTrue(assertNotNull(successorReceipt.innsendteVedlegg).any { it.vedleggsnr == deferred.attachmentCode })
		assertTrue(successorReceipt.skalEttersendes.isNullOrEmpty())
		assertCompletedSubmission(successorId, null, organizationSender, true, expectReceiptDocument = true)
	}

	@Test
	fun `omitted submission user clears a stored other person while preserving organization sender`() {
		val created = createApplication()
		val applicationId = assertNotNull(created.innsendingsId)
		// Draft REST cannot assign parties independently of submission. Emulate an already stored draft.
		applicationTestRepository.setParties(applicationId, other, organizationSender)

		val receipt = api.submitDigitalApplication(created, omitUser = true)
			.assertHttpStatus(HttpStatus.OK).body

		assertReceipt(created, receipt)
		assertCompletedSubmission(applicationId, null, organizationSender, null)
	}

	@Test
	fun `null submission user keeps a previously unidentified party null`() {
		val created = createApplication()
		val applicationId = assertNotNull(created.innsendingsId)
		// Emulate a historical unidentified draft; draft REST cannot express this state.
		applicationTestRepository.setParties(applicationId, null, organizationSender)

		val receipt = api.submitDigitalApplication(
			created,
			bruker = null,
		).assertHttpStatus(HttpStatus.OK).body

		assertReceipt(created, receipt)
		assertCompletedSubmission(applicationId, null, organizationSender, null)
	}

	@Test
	fun `explicit self replaces the previously stored other person`() {
		val created = createApplication()
		val applicationId = assertNotNull(created.innsendingsId)
		// A previous affected party cannot be assigned through draft REST before submission.
		applicationTestRepository.setParties(applicationId, other, organizationSender)

		val receipt = api.submitDigitalApplication(created, bruker = self.id, avsender = personSender)
			.assertHttpStatus(HttpStatus.OK).body

		assertReceipt(created, receipt)
		assertCompletedSubmission(applicationId, self, personSender, null)
	}

	@Test
	fun `invalid user identity is rejected without submission effects`() {
		val created = createApplication()
		val applicationId = assertNotNull(created.innsendingsId)
		val before = api.getSoknadSendinn(applicationId).assertHttpStatus(HttpStatus.OK).body

		val rejected = api.submitDigitalApplication(
			created,
			bruker = "invalid-identity",
			avsender = organizationSender,
		).assertHttpStatus(HttpStatus.BAD_REQUEST).assertErrorCode(ErrorCode.ILLEGAL_ARGUMENT)

		assertNotNull(rejected.errorBody.message)
		val after = api.getSoknadSendinn(applicationId).assertHttpStatus(HttpStatus.OK).body
		assertEquals(before.status, after.status)
		assertEquals(before.innsendtDato, after.innsendtDato)
		assertEquals(before.vedleggsListe.map { it.id to it.opplastingsStatus },
			after.vedleggsListe.map { it.id to it.opplastingsStatus })
		// The rejected request must not mutate hidden party fields either.
		val stored = applicationTestRepository.findBySubmissionId(applicationId)
		assertTrue(stored.affectedUser == self)
		assertNull(stored.sender)
		assertTrue(stored.owner == ApplicationOwner.person(TokenGenerator.subject))
		verify(exactly = 0) {
			receiverTarget.sendInnSoknad(match { it.innsendingsId == applicationId }, any(), any(), any(), any())
			notificationPublisher.avsluttBrukernotifikasjon(match { it.innsendingId == applicationId })
		}
	}

	private fun createApplication(): SkjemaDto {
		val request = SkjemaDtoTestBuilder(brukerId = TokenGenerator.subject, tittel = "Affected party submission").build()
		val created = api.createSoknad(request).assertHttpStatus(HttpStatus.CREATED).body
		val applicationId = assertNotNull(created.innsendingsId)
		val notification = notificationPublisher.awaitNotification(applicationId)
		assertTrue(notification.soknadRef.personId == TokenGenerator.subject)
		val fetched = api.getSoknadSendinn(applicationId).assertHttpStatus(HttpStatus.OK).body
		assertEquals(applicationId, fetched.innsendingsId)
		assertEquals(created.tittel, fetched.tittel)
		return created
	}

	private fun assertReceipt(created: SkjemaDto, receipt: ApplicationSubmissionResponse) {
		assertEquals(created.innsendingsId, receipt.innsendingsId.toString())
		assertEquals(created.tittel, receipt.title)
		assertNotNull(receipt.submittedAt)
		assertNotNull(receipt.mainDocumentFileId)
		assertNull(receipt.ettersendingsId)
		assertTrue(receipt.attachments.isNullOrEmpty())
	}

	private fun assertCompletedSubmission(
		applicationId: String,
		expectedUser: BrukerDto?,
		expectedSender: AvsenderDto,
		expectedAccess: Boolean?,
		expectReceiptDocument: Boolean = false,
	) {
		val capturedUser = slot<BrukerDto?>()
		val capturedSender = slot<AvsenderDto>()
		val capturedAttachments = slot<List<VedleggDto>>()
		val capturedAccess = slot<Boolean?>()
		verify(timeout = 5000, exactly = 1) {
			receiverTarget.sendInnSoknad(
				match { it.innsendingsId == applicationId && it.brukerId == TokenGenerator.subject },
				capture(capturedAttachments),
				capture(capturedSender),
				captureNullable(capturedUser),
				captureNullable(capturedAccess),
			)
		}
		assertTrue(capturedUser.captured == expectedUser, "Outgoing affected party must retain the requested or stored meaning")
		assertTrue(capturedSender.captured == expectedSender, "Outgoing sender must not be replaced by the owner")
		assertEquals(expectedAccess, capturedAccess.captured)
		assertTrue(capturedAttachments.captured.any { it.erHoveddokument && !it.erVariant })
		assertEquals(expectReceiptDocument, capturedAttachments.captured.any { it.vedleggsnr == Constants.KVITTERINGS_NR })
		assertTrue(capturedAttachments.captured.all { it.opplastingsStatus == OpplastingsStatusDto.KlarForInnsending })
		verify(timeout = 5000, exactly = 1) {
			notificationPublisher.avsluttBrukernotifikasjon(match { it.innsendingId == applicationId })
		}
		// Submitted applications cannot be read through draft REST GET. Await the async commit here,
		// and check hidden ownership and SQL NULL semantics that neither receipts nor outgoing calls expose.
		await().atMost(Duration.ofSeconds(5)).untilAsserted {
			val stored = applicationTestRepository.findBySubmissionId(applicationId)
			assertEquals(SoknadsStatus.Innsendt, stored.status)
			assertTrue(stored.owner == ApplicationOwner.person(TokenGenerator.subject) && !stored.ownerIsSqlNull,
				"Submission must preserve the authenticated owner")
			assertTrue(stored.userId == TokenGenerator.subject)
			assertTrue(stored.affectedUser == expectedUser)
			assertEquals(expectedUser == null, stored.affectedUserIsSqlNull)
			assertTrue(stored.sender == expectedSender)
			assertEquals(expectedAccess, stored.grantUserDigitalAccess)
		}
	}
}
