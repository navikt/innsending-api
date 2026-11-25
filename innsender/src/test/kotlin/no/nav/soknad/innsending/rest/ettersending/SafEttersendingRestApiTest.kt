package no.nav.soknad.innsending.rest.ettersending

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkSpyBean
import io.mockk.clearMocks
import io.mockk.verify
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerAPITest
import no.nav.soknad.innsending.model.SkjemaDto
import no.nav.soknad.innsending.repository.FilRepository
import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.repository.VedleggRepository
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.ApplicationOwner
import no.nav.soknad.innsending.service.RepositoryUtils
import no.nav.soknad.innsending.utils.ApiWebClient
import no.nav.soknad.innsending.utils.ApplicationTestRepository
import no.nav.soknad.innsending.utils.TokenGenerator
import no.nav.soknad.innsending.utils.builders.SkjemaDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.ettersending.EksternOpprettEttersendingTestBuilder
import no.nav.soknad.innsending.utils.builders.ettersending.InnsendtVedleggDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.ettersending.OpprettEttersendingTestBuilder
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Import(ApplicationTestRepository::class)
class SafEttersendingRestApiTest : ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var storedApplications: ApplicationTestRepository

	@MockkSpyBean
	lateinit var notificationPublisher: PublisherInterface

	@MockkBean(relaxed = true)
	lateinit var soknadsmottaker: MottakerAPITest

	@Autowired
	lateinit var repo: RepositoryUtils

	@Autowired
	lateinit var soknadRepository: SoknadRepository

	@Autowired
	lateinit var vedleggRepository: VedleggRepository

	@Autowired
	lateinit var filRepository: FilRepository

	@LocalServerPort
	var serverPort: Int = 0

	private lateinit var api: ApiWebClient

	@BeforeEach
	fun setup() {
		filRepository.deleteAll()
		vedleggRepository.deleteAll()
		soknadRepository.deleteAll()
		api = ApiWebClient(webTestClient, serverPort, mockOAuth2Server)
		clearMocks(notificationPublisher)
		clearMocks(soknadsmottaker)
	}

	@Test
	fun `local application about another person gives standalone ettersending`() {
		val skjemanr = "NAV 02-07.05"
		val otherPerson = "10987654321"
		val original = api.createSoknad(applicationRequest(skjemanr)).assertHttpStatus(HttpStatus.CREATED).body
		awaitNotificationCreated(original.innsendingsId!!)
		api.submitDigitalApplication(original, bruker = otherPerson).assertHttpStatus(HttpStatus.OK)
		awaitSubmission(original.innsendingsId)
		verify(exactly = 1) {
			soknadsmottaker.sendInnSoknad(
				match { it.innsendingsId == original.innsendingsId }, any(),
				match { it.id == TokenGenerator.subject }, match { it?.id == otherPerson }, any()
			)
		}
		stubArchivedApplications(emptyList())

		val created = api.createEttersending(OpprettEttersendingTestBuilder().skjemanr(skjemanr).build())
			.assertHttpStatus(HttpStatus.CREATED).body

		val stored = api.getSoknadSendinn(created.innsendingsId!!).assertHttpStatus(HttpStatus.OK).body
		assertEquals(created.innsendingsId, stored.ettersendingsId)
		awaitNotificationCreated(created.innsendingsId)
		verify(exactly = 1) {
			notificationPublisher.opprettBrukernotifikasjon(match {
				it.soknadRef.innsendingId == created.innsendingsId && it.soknadRef.groupId == created.innsendingsId
			})
		}
		verify(exactly = 0) {
			soknadsmottaker.sendInnSoknad(match { it.innsendingsId == created.innsendingsId }, any(), any(), any(), any())
		}
	}

	@Test
	fun `SAF cannot reintroduce a locally known application about another person`() {
		val skjemanr = "NAV 02-07.05"
		val otherPersonApplication = api.createSoknad(applicationRequest(skjemanr)).assertHttpStatus(HttpStatus.CREATED).body
		awaitNotificationCreated(otherPersonApplication.innsendingsId!!)
		api.submitDigitalApplication(otherPersonApplication, bruker = "10987654321").assertHttpStatus(HttpStatus.OK)
		awaitSubmission(otherPersonApplication.innsendingsId)
		stubArchivedApplications(
			listOf(journalpost(
				"synthetic-record", "Synthetic application", otherPersonApplication.innsendingsId!!,
				"FOS", skjemanr, LocalDateTime.now().toString()
			))
		)

		val created = api.createEttersending(OpprettEttersendingTestBuilder().skjemanr(skjemanr).build())
			.assertHttpStatus(HttpStatus.CREATED).body

		val stored = api.getSoknadSendinn(created.innsendingsId!!).assertHttpStatus(HttpStatus.OK).body
		assertEquals(created.innsendingsId, stored.ettersendingsId)
		awaitNotificationCreated(created.innsendingsId)
		verify(exactly = 1) {
			notificationPublisher.opprettBrukernotifikasjon(match {
				it.soknadRef.innsendingId == created.innsendingsId && it.soknadRef.groupId == created.innsendingsId
			})
		}
		verify(exactly = 0) {
			soknadsmottaker.sendInnSoknad(match { it.innsendingsId == created.innsendingsId }, any(), any(), any(), any())
		}
	}

	@Test
	fun `inherited reference to another person gives standalone ettersending`() {
		val skjemanr = "NAV 02-07.05"
		val otherPersonApplication = api.createSoknad(applicationRequest("NAV 99-99.99"))
			.assertHttpStatus(HttpStatus.CREATED).body
		awaitNotificationCreated(otherPersonApplication.innsendingsId!!)
		api.submitDigitalApplication(otherPersonApplication, bruker = "10987654321").assertHttpStatus(HttpStatus.OK)
		awaitSubmission(otherPersonApplication.innsendingsId)
		val candidate = api.createSoknad(applicationRequest(skjemanr)).assertHttpStatus(HttpStatus.CREATED).body
		awaitNotificationCreated(candidate.innsendingsId!!)
		api.submitDigitalApplication(candidate, bruker = TokenGenerator.subject).assertHttpStatus(HttpStatus.OK)
		awaitSubmission(candidate.innsendingsId)
		// REST cannot create a cross-person reference; reproduce an inconsistent historical link only.
		val candidateDb = repo.hentSoknadDb(candidate.innsendingsId!!)
		repo.lagreSoknad(candidateDb.copy(ettersendingsid = otherPersonApplication.innsendingsId))
		stubArchivedApplications(emptyList())

		val created = api.createEttersending(OpprettEttersendingTestBuilder().skjemanr(skjemanr).build())
			.assertHttpStatus(HttpStatus.CREATED).body

		val stored = api.getSoknadSendinn(created.innsendingsId!!).assertHttpStatus(HttpStatus.OK).body
		assertEquals(created.innsendingsId, stored.ettersendingsId)
		awaitNotificationCreated(created.innsendingsId)
		verify(exactly = 1) {
			notificationPublisher.opprettBrukernotifikasjon(match {
				it.soknadRef.innsendingId == created.innsendingsId && it.soknadRef.groupId == created.innsendingsId
			})
		}
		verify(exactly = 0) {
			soknadsmottaker.sendInnSoknad(match { it.innsendingsId == created.innsendingsId }, any(), any(), any(), any())
		}
	}

	@Test
	fun `external ettersending cannot link to an application about another person`() {
		val skjemanr = "NAV 02-07.05"
		val original = api.createSoknad(applicationRequest(skjemanr)).assertHttpStatus(HttpStatus.CREATED).body
		awaitNotificationCreated(original.innsendingsId!!)
		api.submitDigitalApplication(original, bruker = "10987654321").assertHttpStatus(HttpStatus.OK)
		awaitSubmission(original.innsendingsId)
		stubArchivedApplications(emptyList())

		val created = api.createEksternEttersending(
			EksternOpprettEttersendingTestBuilder().skjemanr(skjemanr).vedleggsListe(emptyList())
				.koblesTilEksisterendeSoknad(true).build()
		).assertHttpStatus(HttpStatus.CREATED).body

		val stored = api.getSoknadSendinn(created.innsendingsId!!).assertHttpStatus(HttpStatus.OK).body
		assertEquals(created.innsendingsId, stored.ettersendingsId)
		awaitNotificationCreated(created.innsendingsId)
		verify(exactly = 1) {
			notificationPublisher.opprettBrukernotifikasjon(match {
				it.soknadRef.innsendingId == created.innsendingsId && it.soknadRef.groupId == created.innsendingsId
			})
		}
		verify(exactly = 0) {
			soknadsmottaker.sendInnSoknad(match { it.innsendingsId == created.innsendingsId }, any(), any(), any(), any())
		}
	}

	@Test
	fun `explicit affected user selects older matching application rather than newer other person`() {
		val skjemanr = "NAV 89-47.51"
		val ownApplication = api.createSoknad(applicationRequest(skjemanr)).assertHttpStatus(HttpStatus.CREATED).body
		awaitNotificationCreated(ownApplication.innsendingsId!!)
		val ownSubmission = api.submitDigitalApplication(ownApplication, bruker = TokenGenerator.subject)
			.assertHttpStatus(HttpStatus.OK).body
		awaitSubmission(ownApplication.innsendingsId)
		val otherApplication = api.createSoknad(applicationRequest(skjemanr)).assertHttpStatus(HttpStatus.CREATED).body
		awaitNotificationCreated(otherApplication.innsendingsId!!)
		val otherSubmission = api.submitDigitalApplication(otherApplication, bruker = "10987654321")
			.assertHttpStatus(HttpStatus.OK).body
		awaitSubmission(otherApplication.innsendingsId)
		assertTrue(otherSubmission.submittedAt.isAfter(ownSubmission.submittedAt))
		stubArchivedApplications(emptyList())

		val created = api.createEttersending(OpprettEttersendingTestBuilder().skjemanr(skjemanr).build())
			.assertHttpStatus(HttpStatus.CREATED).body

		val stored = api.getSoknadSendinn(created.innsendingsId!!).assertHttpStatus(HttpStatus.OK).body
		assertEquals(ownApplication.innsendingsId, stored.ettersendingsId)
		awaitNotificationCreated(created.innsendingsId)
		verify(exactly = 1) {
			notificationPublisher.opprettBrukernotifikasjon(match {
				it.soknadRef.innsendingId == created.innsendingsId && it.soknadRef.groupId == ownApplication.innsendingsId
			})
		}
		verify(exactly = 0) {
			soknadsmottaker.sendInnSoknad(match { it.innsendingsId == created.innsendingsId }, any(), any(), any(), any())
		}
	}

	@Test
	fun `missing affected user and user type fall back to applicant`() {
		val skjemanr = "NAV 89-47.51"
		val original = api.createSoknad(applicationRequest(skjemanr)).assertHttpStatus(HttpStatus.CREATED).body
		awaitNotificationCreated(original.innsendingsId!!)
		api.submitDigitalApplication(original, bruker = TokenGenerator.subject).assertHttpStatus(HttpStatus.OK)
		awaitSubmission(original.innsendingsId)
		// Current APIs cannot create the missing party fields found in legacy submissions.
		val originalDb = repo.hentSoknadDb(original.innsendingsId!!)
		repo.lagreSoknad(originalDb.copy(affecteduser = null, brukertype = null))
		stubArchivedApplications(emptyList())

		val created = api.createEttersending(OpprettEttersendingTestBuilder().skjemanr(skjemanr).build())
			.assertHttpStatus(HttpStatus.CREATED).body

		val stored = api.getSoknadSendinn(created.innsendingsId!!).assertHttpStatus(HttpStatus.OK).body
		assertEquals(original.innsendingsId, stored.ettersendingsId)
		awaitNotificationCreated(created.innsendingsId)
		verify(exactly = 1) {
			notificationPublisher.opprettBrukernotifikasjon(match {
				it.soknadRef.innsendingId == created.innsendingsId && it.soknadRef.groupId == original.innsendingsId
			})
		}
		verify(exactly = 0) {
			soknadsmottaker.sendInnSoknad(match { it.innsendingsId == created.innsendingsId }, any(), any(), any(), any())
		}
	}

	@Test
	fun `missing affected user with FNR user type falls back to applicant`() {
		val skjemanr = "NAV 89-47.51"
		val original = api.createSoknad(applicationRequest(skjemanr)).assertHttpStatus(HttpStatus.CREATED).body
		awaitNotificationCreated(original.innsendingsId!!)
		api.submitDigitalApplication(original, bruker = TokenGenerator.subject).assertHttpStatus(HttpStatus.OK)
		awaitSubmission(original.innsendingsId)
		// Current APIs always persist affecteduser when completing a submission.
		val originalDb = repo.hentSoknadDb(original.innsendingsId!!)
		repo.lagreSoknad(originalDb.copy(affecteduser = null))
		stubArchivedApplications(emptyList())

		val created = api.createEttersending(OpprettEttersendingTestBuilder().skjemanr(skjemanr).build())
			.assertHttpStatus(HttpStatus.CREATED).body

		val stored = api.getSoknadSendinn(created.innsendingsId!!).assertHttpStatus(HttpStatus.OK).body
		assertEquals(original.innsendingsId, stored.ettersendingsId)
		awaitNotificationCreated(created.innsendingsId)
		verify(exactly = 1) {
			notificationPublisher.opprettBrukernotifikasjon(match {
				it.soknadRef.innsendingId == created.innsendingsId && it.soknadRef.groupId == original.innsendingsId
			})
		}
		verify(exactly = 0) {
			soknadsmottaker.sendInnSoknad(match { it.innsendingsId == created.innsendingsId }, any(), any(), any(), any())
		}
	}

	@Test
	fun `historical affected user ident selects older matching application rather than newer other person`() {
		val skjemanr = "NAV 89-47.51"
		val historicalIdent = "12345678902"
		val original = api.createSoknad(applicationRequest(skjemanr)).assertHttpStatus(HttpStatus.CREATED).body
		awaitNotificationCreated(original.innsendingsId!!)
		val ownSubmission = api.submitDigitalApplication(original, bruker = historicalIdent)
			.assertHttpStatus(HttpStatus.OK).body
		awaitSubmission(original.innsendingsId)
		verify(exactly = 1) {
			soknadsmottaker.sendInnSoknad(
				match { it.innsendingsId == original.innsendingsId }, any(), any(),
				match { it?.id == historicalIdent }, any()
			)
		}
		val otherApplication = api.createSoknad(applicationRequest(skjemanr)).assertHttpStatus(HttpStatus.CREATED).body
		awaitNotificationCreated(otherApplication.innsendingsId!!)
		val otherSubmission = api.submitDigitalApplication(otherApplication, bruker = "10987654321")
			.assertHttpStatus(HttpStatus.OK).body
		awaitSubmission(otherApplication.innsendingsId)
		assertTrue(otherSubmission.submittedAt.isAfter(ownSubmission.submittedAt))
		stubArchivedApplications(emptyList())

		val created = api.createEttersending(OpprettEttersendingTestBuilder().skjemanr(skjemanr).build())
			.assertHttpStatus(HttpStatus.CREATED).body

		val stored = api.getSoknadSendinn(created.innsendingsId!!).assertHttpStatus(HttpStatus.OK).body
		assertEquals(original.innsendingsId, stored.ettersendingsId)
		awaitNotificationCreated(created.innsendingsId)
		verify(exactly = 1) {
			notificationPublisher.opprettBrukernotifikasjon(match {
				it.soknadRef.innsendingId == created.innsendingsId && it.soknadRef.groupId == original.innsendingsId
			})
		}
		verify(exactly = 0) {
			soknadsmottaker.sendInnSoknad(match { it.innsendingsId == created.innsendingsId }, any(), any(), any(), any())
		}
	}

	@Test
	fun `applications outside applicant form status or date scope cannot be selected`() {
		val skjemanr = "NAV 02-07.05"
		val otherApplicant = "10987654321"
		stubApplicantIdents(otherApplicant)
		val otherToken = TokenGenerator(mockOAuth2Server).lagTokenXToken(fnr = otherApplicant)
		val otherApplicantApplication = api.createSoknad(applicationRequest(skjemanr), authToken = otherToken)
			.assertHttpStatus(HttpStatus.CREATED).body
		awaitNotificationCreated(otherApplicantApplication.innsendingsId!!)
		api.submitDigitalApplication(otherApplicantApplication, authToken = otherToken, bruker = TokenGenerator.subject)
			.assertHttpStatus(HttpStatus.OK)
		awaitSubmission(otherApplicantApplication.innsendingsId)

		val otherFormApplication = api.createSoknad(applicationRequest("NAV 99-99.99"))
			.assertHttpStatus(HttpStatus.CREATED).body
		awaitNotificationCreated(otherFormApplication.innsendingsId!!)
		api.submitDigitalApplication(otherFormApplication, bruker = TokenGenerator.subject).assertHttpStatus(HttpStatus.OK)
		awaitSubmission(otherFormApplication.innsendingsId)

		val unsubmittedApplication = api.createSoknad(applicationRequest(skjemanr)).assertHttpStatus(HttpStatus.CREATED).body
		awaitNotificationCreated(unsubmittedApplication.innsendingsId!!)
		val oldApplication = api.createSoknad(applicationRequest(skjemanr)).assertHttpStatus(HttpStatus.CREATED).body
		awaitNotificationCreated(oldApplication.innsendingsId!!)
		api.submitDigitalApplication(oldApplication, bruker = TokenGenerator.subject).assertHttpStatus(HttpStatus.OK)
		awaitSubmission(oldApplication.innsendingsId)
		// Submission dates are server-generated; REST cannot create an application submitted four years ago.
		val oldApplicationDb = repo.hentSoknadDb(oldApplication.innsendingsId!!)
		repo.lagreSoknad(oldApplicationDb.copy(innsendtdato = LocalDateTime.now().minusYears(4)))
		stubArchivedApplications(emptyList())

		val created = api.createEttersending(OpprettEttersendingTestBuilder().skjemanr(skjemanr).build())
			.assertHttpStatus(HttpStatus.CREATED).body

		val stored = api.getSoknadSendinn(created.innsendingsId!!).assertHttpStatus(HttpStatus.OK).body
		assertEquals(created.innsendingsId, stored.ettersendingsId)
		awaitNotificationCreated(created.innsendingsId)
		verify(exactly = 1) {
			notificationPublisher.opprettBrukernotifikasjon(match {
				it.soknadRef.innsendingId == created.innsendingsId && it.soknadRef.groupId == created.innsendingsId
			})
		}
		verify(exactly = 0) {
			soknadsmottaker.sendInnSoknad(match { it.innsendingsId == created.innsendingsId }, any(), any(), any(), any())
		}
	}

	@Test
	fun `direct original reference is preserved while newer multi-hop reference is rejected`() {
		val skjemanr = "NAV 02-07.05"
		stubArchivedApplications(emptyList())
		val original = api.createSoknad(applicationRequest(skjemanr)).assertHttpStatus(HttpStatus.CREATED).body
		awaitNotificationCreated(original.innsendingsId!!)
		api.submitDigitalApplication(original, bruker = TokenGenerator.subject).assertHttpStatus(HttpStatus.OK)
		awaitSubmission(original.innsendingsId)

		val followUp = api.createEttersending(ettersendingWithAttachment(skjemanr)).assertHttpStatus(HttpStatus.CREATED).body
		assertEquals(original.innsendingsId, followUp.ettersendingsId)
		val followUpId = followUp.innsendingsId!!
		awaitNotificationCreated(followUpId)
		api.uploadFile(followUpId, followUp.vedleggsListe.single().id!!).assertHttpStatus(HttpStatus.CREATED)
		api.sendInnSoknad(followUpId).assertHttpStatus(HttpStatus.OK)
		awaitSubmission(followUpId)

		val invalidFollowUp = api.createEttersending(ettersendingWithAttachment(skjemanr))
			.assertHttpStatus(HttpStatus.CREATED).body
		assertEquals(original.innsendingsId, invalidFollowUp.ettersendingsId)
		val invalidFollowUpId = invalidFollowUp.innsendingsId!!
		awaitNotificationCreated(invalidFollowUpId)
		api.uploadFile(invalidFollowUpId, invalidFollowUp.vedleggsListe.single().id!!)
			.assertHttpStatus(HttpStatus.CREATED)
		api.sendInnSoknad(invalidFollowUpId).assertHttpStatus(HttpStatus.OK)
		awaitSubmission(invalidFollowUpId)
		// REST always preserves the original reference; only the invalid historical link needs a database fixture.
		val invalidFollowUpDb = repo.hentSoknadDb(invalidFollowUpId)
		repo.lagreSoknad(invalidFollowUpDb.copy(ettersendingsid = followUp.innsendingsId))

		val created = api.createEttersending(OpprettEttersendingTestBuilder().skjemanr(skjemanr).build())
			.assertHttpStatus(HttpStatus.CREATED).body

		val stored = api.getSoknadSendinn(created.innsendingsId!!).assertHttpStatus(HttpStatus.OK).body
		assertEquals(original.innsendingsId, stored.ettersendingsId)
		awaitNotificationCreated(created.innsendingsId)
		verify(exactly = 1) {
			notificationPublisher.opprettBrukernotifikasjon(match {
				it.soknadRef.innsendingId == created.innsendingsId && it.soknadRef.groupId == original.innsendingsId
			})
		}
		verify(exactly = 0) {
			soknadsmottaker.sendInnSoknad(match { it.innsendingsId == created.innsendingsId }, any(), any(), any(), any())
		}
	}

	private fun applicationRequest(skjemanr: String): SkjemaDto = SkjemaDtoTestBuilder(skjemanr = skjemanr).build()

	private fun ettersendingWithAttachment(skjemanr: String) = OpprettEttersendingTestBuilder().skjemanr(skjemanr)
		.vedleggsListe(listOf(InnsendtVedleggDtoTestBuilder().vedleggsnr("N6").build())).build()

	private fun awaitNotificationCreated(innsendingsId: String?) {
		requireNotNull(innsendingsId)
		verify(timeout = 5000, exactly = 1) {
			notificationPublisher.opprettBrukernotifikasjon(match { it.soknadRef.innsendingId == innsendingsId })
		}
	}

	private fun awaitSubmission(innsendingsId: String?) {
		requireNotNull(innsendingsId)
		verify(timeout = 5000, exactly = 1) {
			soknadsmottaker.sendInnSoknad(
				match { it.innsendingsId == innsendingsId }, any(), any(), any(), any()
			)
		}
		verify(timeout = 5000, exactly = 1) {
			notificationPublisher.avsluttBrukernotifikasjon(match { it.innsendingId == innsendingsId })
		}
		// Both REST GET endpoints reject submitted applications; wait for the asynchronous commit via the database.
		await().atMost(Duration.ofSeconds(10)).untilAsserted {
			assertEquals(SoknadsStatus.Innsendt, repo.hentSoknadDb(innsendingsId).status)
		}
	}

	private fun stubApplicantIdents(applicant: String) {
		wireMock.stubFor(
			post(urlPathEqualTo("/pdl-api/graphql")).atPriority(1)
				.withRequestBody(matchingJsonPath("$.operationName", equalTo("HentIdenter")))
				.withRequestBody(matchingJsonPath("$.variables.ident", equalTo(applicant)))
				.willReturn(okJson(jacksonObjectMapper().writeValueAsString(mapOf(
					"data" to mapOf("hentIdenter" to mapOf("identer" to listOf(
						mapOf("ident" to applicant, "gruppe" to "FOLKEREGISTERIDENT", "historisk" to false)
					)))
				))))
		)
	}

	private fun stubArchivedApplications(journalposts: List<Map<String, Any>>) {
		wireMock.stubFor(
			post(urlPathEqualTo("/saf-api/graphql")).atPriority(1).willReturn(
				aResponse().withStatus(200).withHeader("Content-Type", "application/json")
					.withBody(jacksonObjectMapper().writeValueAsString(
						mapOf("data" to mapOf("dokumentoversiktSelvbetjening" to mapOf("journalposter" to journalposts)))
					))
			)
		)
	}

	@Test
	fun `large SAF response still links ettersending to matching archived application`() {
		val skjemanr = "NAV 89-47.51"
		val archiveReference = UUID.randomUUID().toString()
		val requestTitle = "Synthetic archived application follow-up"
		val responseBody = largeSafResponse(skjemanr, archiveReference)
		assertTrue(responseBody.toByteArray(Charsets.UTF_8).size > 256 * 1024)
		wireMock.stubFor(
			post(urlPathEqualTo("/saf-api/graphql"))
				.atPriority(1)
				.willReturn(
					aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody(responseBody)
				)
		)
		// Create a real previous follow-up whose original exists only in SAF.
		val previousFollowUp = api.createEttersending(ettersendingWithAttachment(skjemanr))
			.assertHttpStatus(HttpStatus.CREATED).body
		assertEquals(archiveReference, previousFollowUp.ettersendingsId)
		val previousFollowUpId = previousFollowUp.innsendingsId!!
		awaitNotificationCreated(previousFollowUpId)
		api.uploadFile(previousFollowUpId, previousFollowUp.vedleggsListe.single().id!!)
			.assertHttpStatus(HttpStatus.CREATED)
		api.sendInnSoknad(previousFollowUpId).assertHttpStatus(HttpStatus.OK)
		awaitSubmission(previousFollowUpId)

		val request = OpprettEttersendingTestBuilder()
			.skjemanr(skjemanr)
			.tema("FOS")
			.tittel(requestTitle)
			.build()

		val created = api.createEttersending(request)
			.assertHttpStatus(HttpStatus.CREATED)
			.body
		assertEquals(archiveReference, created.ettersendingsId)

		val fromRest = api.getSoknadSendinn(created.innsendingsId!!)
			.assertHttpStatus(HttpStatus.OK)
			.body
		assertEquals(archiveReference, fromRest.ettersendingsId)
		// Owner is not exposed through REST. Inspect only this internal field in the database.
		val stored = storedApplications.findBySubmissionId(created.innsendingsId!!)
		assertTrue(stored.owner == ApplicationOwner.person(TokenGenerator.subject), "The supplementary application must persist the logged-in owner")
		assertEquals(false, stored.ownerIsSqlNull)

		wireMock.verify(2, postRequestedFor(urlPathEqualTo("/saf-api/graphql")))
		verify(timeout = 5000, exactly = 1) {
			notificationPublisher.opprettBrukernotifikasjon(
				match { it.soknadRef.innsendingId == created.innsendingsId }
			)
		}
		val notifications = mutableListOf<AddNotification>()
		verify { notificationPublisher.opprettBrukernotifikasjon(capture(notifications)) }
		val notification = notifications.single { it.soknadRef.innsendingId == created.innsendingsId }
		assertEquals(created.innsendingsId, notification.soknadRef.innsendingId)
		assertEquals(archiveReference, notification.soknadRef.groupId)
		assertEquals(created.brukerId, notification.soknadRef.personId)
		assertEquals(false, notification.soknadRef.erSystemGenerert)
		assertEquals(true, notification.soknadRef.erEttersendelse)
		assertEquals(
			"Ettersend manglende vedlegg til: $requestTitle",
			notification.brukernotifikasjonInfo.notifikasjonsTittel
		)
		assertEquals(29, notification.brukernotifikasjonInfo.antallAktiveDager)
	}

	private fun largeSafResponse(skjemanr: String, archiveReference: String): String {
		val journalposts = buildList {
			add(
				journalpost(
					journalpostId = "relevant-archive-record",
					title = "Synthetic archived application",
					externalReferenceId = archiveReference,
					theme = "FOS",
					formCode = skjemanr,
					date = LocalDate.now().minusDays(1).atTime(12, 0).toString()
				)
			)
			repeat(700) { index ->
				add(
					journalpost(
						journalpostId = "other-record-$index",
						title = "Archived journalpost $index ${"moderate title segment ".repeat(12)}",
						externalReferenceId = "other-archive-record-$index",
						theme = "AAP",
						formCode = "NAV 99-99.99",
						date = LocalDate.now().minusDays(30).atTime(12, 0).toString()
					)
				)
			}
		}
		return jacksonObjectMapper().writeValueAsString(
			mapOf("data" to mapOf("dokumentoversiktSelvbetjening" to mapOf("journalposter" to journalposts)))
		)
	}

	private fun journalpost(
		journalpostId: String,
		title: String,
		externalReferenceId: String,
		theme: String,
		formCode: String,
		date: String,
	): Map<String, Any> = mapOf(
		"journalpostId" to journalpostId,
		"tittel" to title,
		"eksternReferanseId" to externalReferenceId,
		"journalstatus" to "JOURNALFOERT",
		"journalposttype" to "I",
		"tema" to theme,
		"kanal" to "NAV_NO",
		"relevanteDatoer" to listOf(mapOf("dato" to date, "datotype" to "DATO_OPPRETTET")),
		"dokumenter" to listOf(
			mapOf("brevkode" to formCode, "tittel" to formCode),
			mapOf("brevkode" to "N6", "tittel" to "Synthetic attachment")
		)
	)
}
