package no.nav.soknad.innsending.rest

import com.ninjasquad.springmockk.MockkSpyBean
import io.mockk.clearMocks
import io.mockk.slot
import io.mockk.verify
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerAPITest
import no.nav.soknad.innsending.model.AvsenderDto
import no.nav.soknad.innsending.model.BrukerDto
import no.nav.soknad.innsending.model.EksternEttersendingsOppgave
import no.nav.soknad.innsending.model.InnsendtVedleggDto
import no.nav.soknad.innsending.model.OpprettLospost
import no.nav.soknad.innsending.model.SkjemaDto
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.ApplicationOwner
import no.nav.soknad.innsending.service.config.ConfigDefinition
import no.nav.soknad.innsending.utils.ApiWebClient
import no.nav.soknad.innsending.utils.ApplicationTestRepository
import no.nav.soknad.innsending.utils.TokenGenerator
import no.nav.soknad.innsending.utils.awaitNotification
import no.nav.soknad.innsending.utils.builders.SkjemaDtoTestBuilder
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.util.AopTestUtils
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Import(ApplicationTestRepository::class)
class OwnerWritingRestApiTest : ApplicationTest() {

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
	private val otherIdentity = "10987654321"
	private val receiverTarget: MottakerAPITest
		get() = AopTestUtils.getUltimateTargetObject(submissionReceiver)

	@BeforeEach
	fun setup() {
		api = ApiWebClient(webTestClient, serverPort, mockOAuth2Server)
		clearMocks(notificationPublisher, receiverTarget, answers = false)
	}

	@Test
	fun `digital creation writes the token identity as owner instead of the request identity`() {
		val request = SkjemaDtoTestBuilder(brukerId = otherIdentity).build()
		val token = TokenGenerator(mockOAuth2Server).lagTokenXToken(TokenGenerator.subject)
		val created = api.createSoknad(request, authToken = token)
			.assertHttpStatus(HttpStatus.CREATED).body
		val applicationId = assertNotNull(created.innsendingsId)
		val notification = notificationPublisher.awaitNotification(applicationId)
		assertEquals(TokenGenerator.subject, notification.soknadRef.personId)

		assertEquals(TokenGenerator.subject, created.brukerId)
		assertEquals(request.tittel, created.tittel)
		assertOwner(applicationId, TokenGenerator.subject)
		val fetched = api.getSoknadSendinn(applicationId).assertHttpStatus(HttpStatus.OK).body
		assertEquals(applicationId, fetched.innsendingsId)
		assertEquals(TokenGenerator.subject, fetched.brukerId)
	}

	@ParameterizedTest(name = "owner present: {0}")
	@ValueSource(booleans = [true, false])
	fun `ordinary form updates preserve the stored owner despite another request identity`(ownerPresent: Boolean) {
		val original = SkjemaDtoTestBuilder(brukerId = TokenGenerator.subject).build()
		val created = createApplication(original)
		val applicationId = assertNotNull(created.innsendingsId)
		assertOwner(applicationId, TokenGenerator.subject)
		// These historical owner states cannot be assigned through the public API.
		val storedOwner = if (ownerPresent) ApplicationOwner.person("12345678902") else null
		applicationTestRepository.setOwner(applicationId, storedOwner)
		val request = original.copy(brukerId = otherIdentity, tittel = "Updated application title")

		val response = api.updateSoknad(applicationId, request)
		assertEquals(HttpStatus.OK, response.statusCode)
		val updated = assertNotNull(response.body)
		assertEquals(request.tittel, updated.tittel)
		assertEquals(TokenGenerator.subject, updated.brukerId)

		val fetched = api.getSoknadSendinn(applicationId).assertHttpStatus(HttpStatus.OK).body
		assertEquals(applicationId, fetched.innsendingsId)
		assertEquals(request.tittel, fetched.tittel)
		assertEquals(TokenGenerator.subject, fetched.brukerId)
		val mainDocuments = fetched.vedleggsListe.filter { it.erHoveddokument }
		assertEquals(2, mainDocuments.size)
		assertEquals(setOf(false, true), mainDocuments.map { it.erVariant }.toSet())
		assertEquals(setOf(original.skjemanr), mainDocuments.map { it.vedleggsnr }.toSet())
		// Owner is not exposed by REST, so inspect only that hidden state through the test repository.
		val stored = applicationTestRepository.findBySubmissionId(applicationId)
		assertEquals(!ownerPresent, stored.ownerIsSqlNull)
		assertEquals(storedOwner, stored.owner)
	}

	@ParameterizedTest(name = "owner present: {0}")
	@ValueSource(booleans = [true, false])
	fun `form completion preserves the stored owner despite another request identity`(ownerPresent: Boolean) {
		val original = SkjemaDtoTestBuilder(brukerId = TokenGenerator.subject).build()
		val created = createApplication(original)
		val applicationId = assertNotNull(created.innsendingsId)
		assertOwner(applicationId, TokenGenerator.subject)
		// These historical owner states cannot be assigned through the public API.
		val storedOwner = if (ownerPresent) ApplicationOwner.person("12345678902") else null
		applicationTestRepository.setOwner(applicationId, storedOwner)
		val request = original.copy(brukerId = otherIdentity, tittel = "Updated application title")

		val response = api.utfyltSoknad(applicationId, request)
		assertEquals(HttpStatus.FOUND, response.statusCode)
		assertNotNull(response.headers.location)
		assertNull(response.body)

		val fetched = api.getSoknadSendinn(applicationId).assertHttpStatus(HttpStatus.OK).body
		assertEquals(applicationId, fetched.innsendingsId)
		assertEquals(request.tittel, fetched.tittel)
		assertEquals(TokenGenerator.subject, fetched.brukerId)
		val mainDocuments = fetched.vedleggsListe.filter { it.erHoveddokument }
		assertEquals(2, mainDocuments.size)
		assertEquals(setOf(false, true), mainDocuments.map { it.erVariant }.toSet())
		assertEquals(setOf(original.skjemanr), mainDocuments.map { it.vedleggsnr }.toSet())
		// Owner is not exposed by REST, so inspect only that hidden state through the test repository.
		val stored = applicationTestRepository.findBySubmissionId(applicationId)
		assertEquals(!ownerPresent, stored.ownerIsSqlNull)
		assertEquals(storedOwner, stored.owner)
	}

	@Test
	fun `Nav initiated task creation writes the explicit request user as owner`() {
		val request = EksternEttersendingsOppgave(
			brukerId = otherIdentity,
			skjemanr = "NAV 55-00.60",
			sprak = "nb_NO",
			tema = "BID",
			tittel = "Requested documentation",
			vedleggsListe = listOf(InnsendtVedleggDto(vedleggsnr = "N6", tittel = "Supporting documentation")),
			koblesTilEksisterendeSoknad = false,
		)

		val response = api.createEttersendingsOppgave(request)
		assertEquals(HttpStatus.CREATED, response.statusCode)
		val created = assertNotNull(response.body)
		val applicationId = assertNotNull(created.innsendingsId)
		val notification = notificationPublisher.awaitNotification(applicationId)
		assertEquals(request.brukerId, notification.soknadRef.personId)
		assertEquals(request.brukerId, created.brukerId)
		assertEquals(request.skjemanr, created.skjemanr)
		assertEquals(request.tittel, created.tittel)
		assertOwner(applicationId, request.brukerId)

		val listed = api.oppgaveHentSoknaderForSkjemanr(request.skjemanr, request.brukerId, null, null)
		assertEquals(HttpStatus.OK, listed.statusCode)
		val listedApplication = assertNotNull(listed.body).single { it.innsendingsId == applicationId }
		assertEquals(request.brukerId, listedApplication.brukerId)
	}

	@Test
	fun `lospost creation writes the authenticated user as owner`() {
		val request = OpprettLospost(
			soknadTittel = "Additional documentation",
			tema = "BIL",
			dokumentTittel = "Supporting document",
			sprak = "nb",
		)
		val created = api.createLospost(request).assertHttpStatus(HttpStatus.CREATED).body
		val applicationId = assertNotNull(created.innsendingsId)
		val notification = notificationPublisher.awaitNotification(applicationId)
		assertEquals(TokenGenerator.subject, notification.soknadRef.personId)
		assertEquals(request.soknadTittel, created.tittel)
		assertEquals(TokenGenerator.subject, created.brukerId)
		assertOwner(applicationId, TokenGenerator.subject)

		val fetched = api.getSoknadSendinn(applicationId).assertHttpStatus(HttpStatus.OK).body
		assertEquals(applicationId, fetched.innsendingsId)
		assertEquals(request.soknadTittel, fetched.tittel)
	}

	@ParameterizedTest(name = "request includes user: {0}")
	@ValueSource(booleans = [false, true])
	fun `no-login submission keeps SQL null owner and the existing business identity payload`(includesUser: Boolean) {
		api.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "on").assertHttpStatus(HttpStatus.OK)
		val applicationId = UUID.randomUUID().toString()
		val requestUser = if (includesUser) TokenGenerator.subject else null
		val sender = AvsenderDto(id = otherIdentity, idType = AvsenderDto.IdType.FNR, navn = "Synthetic sender")
		val expectedUser = requestUser?.let { BrukerDto(id = it, idType = BrukerDto.IdType.FNR) }

		val response = api.submitNologinApplication(
			innsendingsId = applicationId,
			title = "No-login owner writing test",
			brukerId = requestUser,
			avsender = sender,
			grantUserDigitalAccess = true,
		).assertHttpStatus(HttpStatus.OK)
		assertEquals(applicationId, response.body.innsendingsId.toString())
		assertEquals("No-login owner writing test", response.body.title)
		assertNotNull(response.body.submittedAt)
		assertNull(response.body.mainDocumentFileId)
		assertNull(response.body.ettersendingsId)
		assertTrue(response.body.attachments.isNullOrEmpty())

		val capturedUser = slot<BrukerDto?>()
		verify(timeout = 5000, exactly = 1) {
			receiverTarget.sendInnSoknad(
				match { it.innsendingsId == applicationId && it.brukerId == requestUser },
				any(),
				eq(sender),
				captureNullable(capturedUser),
				eq(true),
			)
		}
		assertTrue(capturedUser.captured == expectedUser, "The submission user must retain its existing request meaning")

		// Submitted no-login rows have no readable end-user GET. Await the async commit and inspect internal fields.
		await().atMost(Duration.ofSeconds(5)).untilAsserted {
			val stored = applicationTestRepository.findBySubmissionId(applicationId)
			assertEquals(SoknadsStatus.Innsendt, stored.status)
			assertTrue(stored.ownerIsSqlNull, "No-login ownership must remain SQL NULL")
			assertNull(stored.owner)
			assertEquals(requestUser, stored.userId)
			assertEquals(expectedUser, stored.affectedUser)
			assertEquals(expectedUser == null, stored.affectedUserIsSqlNull)
			assertEquals(sender, stored.sender)
			assertEquals(true, stored.grantUserDigitalAccess)
		}
		verify(exactly = 0) {
			notificationPublisher.opprettBrukernotifikasjon(match { it.soknadRef.innsendingId == applicationId })
		}
	}

	@Test
	fun `legacy application with SQL null owner remains readable and listed without backfill`() {
		val created = createApplication()
		val applicationId = assertNotNull(created.innsendingsId)
		assertOwner(applicationId, TokenGenerator.subject)
		// Emulate a pre-release row. Release one must not require or backfill ownership during reads.
		applicationTestRepository.setOwner(applicationId, null)

		val fetched = api.getSoknadSendinn(applicationId).assertHttpStatus(HttpStatus.OK).body
		assertEquals(applicationId, fetched.innsendingsId)
		assertEquals(TokenGenerator.subject, fetched.brukerId)
		val filledForm = api.getSoknad(applicationId)
		assertEquals(HttpStatus.OK, filledForm.statusCode)
		assertEquals(applicationId, assertNotNull(filledForm.body).innsendingsId)
		val listed = api.getExistingSoknader(created.skjemanr).assertHttpStatus(HttpStatus.OK).body
		assertEquals(listOf(applicationId), listed.map { it.innsendingsId })
		// SQL NULL is internal state and cannot be distinguished from a populated owner through the API.
		assertTrue(applicationTestRepository.findBySubmissionId(applicationId).ownerIsSqlNull,
			"Reading a legacy application must leave its owner SQL NULL")
	}

	private fun createApplication(request: SkjemaDto = SkjemaDtoTestBuilder(brukerId = TokenGenerator.subject).build()): SkjemaDto {
		val created = api.createSoknad(request).assertHttpStatus(HttpStatus.CREATED).body
		val notification = notificationPublisher.awaitNotification(assertNotNull(created.innsendingsId))
		assertEquals(TokenGenerator.subject, notification.soknadRef.personId)
		return created
	}

	private fun assertOwner(applicationId: String, expectedIdentity: String) {
		// Owner is an internal entity field, so its persisted value cannot be asserted through REST GET.
		val stored = applicationTestRepository.findBySubmissionId(applicationId)
		assertTrue(!stored.ownerIsSqlNull && stored.owner == ApplicationOwner.person(expectedIdentity),
			"Creation must persist the expected person owner as a JSON object")
	}

}
