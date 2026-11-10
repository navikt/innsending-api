package no.nav.soknad.innsending.rest

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.ninjasquad.springmockk.MockkSpyBean
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.clearMocks
import io.mockk.verify
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerAPITest
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.BrukerDto
import no.nav.soknad.innsending.model.AvsenderDto
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.PatchVedleggDto
import no.nav.soknad.innsending.model.SoknadType
import no.nav.soknad.innsending.service.EttersendingService
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.model.EksternEttersendingsOppgave
import no.nav.soknad.innsending.model.InnsendtVedleggDto
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.ApplicationOwner
import no.nav.soknad.innsending.repository.domain.models.SoknadDbData
import no.nav.soknad.innsending.utils.ApiWebClient
import no.nav.soknad.innsending.utils.ApplicationTestRepository
import no.nav.soknad.innsending.utils.TokenGenerator
import no.nav.soknad.innsending.utils.awaitNotification
import no.nav.soknad.innsending.utils.builders.SoknadDbDataTestBuilder
import no.nav.soknad.innsending.utils.builders.ettersending.OpprettEttersendingTestBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.util.AopTestUtils
import org.slf4j.LoggerFactory
import org.awaitility.Awaitility.await
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Import(ApplicationTestRepository::class)
class SupplementaryApplicationOwnerIntegrationTest : ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var applications: SoknadRepository

	@Autowired
	lateinit var storedApplications: ApplicationTestRepository

	@MockkSpyBean
	lateinit var notifications: PublisherInterface

	@MockkSpyBean
	lateinit var receiver: MottakerAPITest

	@LocalServerPort
	var serverPort: Int = 0

	private lateinit var api: ApiWebClient
	private val fixtureIds = mutableListOf<String>()
	private val notificationTarget: PublisherInterface
		get() = AopTestUtils.getUltimateTargetObject(notifications)
	private val receiverTarget: MottakerAPITest
		get() = AopTestUtils.getUltimateTargetObject(receiver)
	private val otherPerson = "10987654321"
	private val formNumber = "NAV 55-00.60"

	@BeforeEach
	fun setup() {
		api = ApiWebClient(webTestClient, serverPort, mockOAuth2Server)
		clearMocks(notificationTarget, receiverTarget, answers = false)
		stubArchive()
		stubPerson(otherPerson)
	}

	@AfterEach
	fun removeHistoricalFixtures() {
		storedApplications.deleteApplications(fixtureIds)
	}

	@ParameterizedTest(name = "source owner present: {0}")
	@ValueSource(booleans = [true, false])
	fun `person initiated supplementary application gets the logged in owner instead of the stored original owner`(ownerPresent: Boolean) {
		val original = historicalApplication(owner = if (ownerPresent) ApplicationOwner.person(otherPerson) else null)
		stubArchive(archiveRecord(original.innsendingsid))
		val request = OpprettEttersendingTestBuilder().skjemanr(formNumber).build()

		val created = api.createEttersending(request).assertHttpStatus(HttpStatus.CREATED).body.also(::trackCreated)

		assertPersonApplication(created, original.innsendingsid)
		assertOwner(created, ApplicationOwner.person(TokenGenerator.subject))
	}

	@ParameterizedTest(name = "source owner present: {0}")
	@ValueSource(booleans = [true, false])
	fun `person initiated archived supplementary application gets the logged in owner instead of the original owner`(ownerPresent: Boolean) {
		val original = historicalApplication(userId = otherPerson, owner = if (ownerPresent) ApplicationOwner.person(otherPerson) else null)
		stubArchive(archiveRecord(original.innsendingsid))
		val request = OpprettEttersendingTestBuilder().skjemanr(formNumber).build()

		val created = api.createEttersending(request).assertHttpStatus(HttpStatus.CREATED).body.also(::trackCreated)

		assertPersonApplication(created, original.innsendingsid)
		assertOwner(created, ApplicationOwner.person(TokenGenerator.subject))
	}

	@Test
	fun `Nav initiated supplementary application inherits the stored owner even when it differs from the request user`() {
		val owner = ApplicationOwner.person(otherPerson)
		val original = historicalApplication(owner = owner)

		val response = api.createEttersendingsOppgave(navRequest())

		assertEquals(HttpStatus.CREATED, response.statusCode)
		val created = assertNotNull(response.body).also(::trackCreated)
		assertNavApplication(created, original.innsendingsid)
		assertOwner(created, owner)
	}

	@Test
	fun `Nav initiated archived supplementary application inherits the stored owner even when it differs from the request user`() {
		val owner = ApplicationOwner.person(otherPerson)
		// Only SAF's recent archive record is eligible; the local submission is outside the linking window.
		val original = historicalApplication(userId = otherPerson, owner = owner,
			submittedAt = LocalDateTime.now().minusDays(Constants.MAX_AKTIVE_DAGER + 1))
		stubArchive(archiveRecord(original.innsendingsid))

		val response = api.createEttersendingsOppgave(navRequest())

		assertEquals(HttpStatus.CREATED, response.statusCode)
		val created = assertNotNull(response.body).also(::trackCreated)
		assertNavApplication(created, original.innsendingsid)
		assertOwner(created, owner)
	}

	@Test
	fun `Nav archived original without a local row retains the established request owner rule`() {
		val archivedId = UUID.randomUUID().toString()
		stubArchive(archiveRecord(archivedId))

		val response = api.createEttersendingsOppgave(navRequest())

		assertEquals(HttpStatus.CREATED, response.statusCode)
		val created = assertNotNull(response.body).also(::trackCreated)
		assertNavApplication(created, archivedId, TokenGenerator.subject)
		assertOwner(created, ApplicationOwner.person(TokenGenerator.subject))
	}

	@Test
	fun `Nav lookup task list and duplicate check recognize historical affected person identifiers`() {
		val original = historicalApplication(owner = ApplicationOwner.person(otherPerson))
		stubPerson(TokenGenerator.subject, historicalIds = listOf("12345678902"))
		// PDL confirms this historical identifier for the request person, not for the unrelated owner.
		storedApplications.setParties(original.innsendingsid, BrukerDto("12345678902", BrukerDto.IdType.FNR), null)

		val response = api.createEttersendingsOppgave(navRequest())

		assertEquals(HttpStatus.CREATED, response.statusCode)
		val created = assertNotNull(response.body).also(::trackCreated)
		assertNavApplication(created, original.innsendingsid)
		assertOwner(created, ApplicationOwner.person(otherPerson))
		// Repeat through REST so both the task lookup and duplicate warning operate on the stored historical party.
		assertDuplicateWarning(navRequest(), original.innsendingsid, otherPerson)
	}

	@Test
	fun `authorized Nav task list returns personal supplementary applications for the affected person without rewriting legacy identity`() {
		val original = historicalApplication()
		storedApplications.setParties(original.innsendingsid, BrukerDto(otherPerson, BrukerDto.IdType.FNR), null)
		val created = api.createEttersending(OpprettEttersendingTestBuilder().skjemanr(formNumber).build())
			.assertHttpStatus(HttpStatus.CREATED).body.also(::trackCreated)
		assertPersonApplication(created, original.innsendingsid)

		val response = api.oppgaveHentSoknaderForSkjemanr(formNumber, otherPerson, null, null)

		assertEquals(HttpStatus.OK, response.statusCode)
		val listed = assertNotNull(response.body).single { it.innsendingsId == created.innsendingsId }
		assertEquals(TokenGenerator.subject, listed.brukerId)
		val token = TokenGenerator(mockOAuth2Server).lagTokenXToken(otherPerson)
		api.getSoknadSendinn(assertNotNull(created.innsendingsId), token).assertHttpStatus(HttpStatus.NOT_FOUND)
	}

	@Test
	fun `local no-login original is skipped in favour of an older authenticated original`() {
		val original = historicalApplication(submittedAt = LocalDateTime.now().minusDays(2))
		historicalApplication(viewType = VisningsType.nologin)
		val request = OpprettEttersendingTestBuilder().skjemanr(formNumber).build()

		val created = api.createEttersending(request).assertHttpStatus(HttpStatus.CREATED).body.also(::trackCreated)

		assertPersonApplication(created, original.innsendingsid)
		assertOwner(created, ApplicationOwner.person(TokenGenerator.subject))
	}

	@Test
	fun `archived original marked no-login locally is skipped even if the archive channel says otherwise`() {
		val ignored = historicalApplication(userId = otherPerson, viewType = VisningsType.nologin)
		val original = historicalApplication(userId = otherPerson, submittedAt = LocalDateTime.now().minusDays(2))
		stubArchive(archiveRecord(ignored.innsendingsid), archiveRecord(original.innsendingsid, daysAgo = 2))
		val request = OpprettEttersendingTestBuilder().skjemanr(formNumber).build()

		val created = api.createEttersending(request).assertHttpStatus(HttpStatus.CREATED).body.also(::trackCreated)

		assertPersonApplication(created, original.innsendingsid)
	}

	@Test
	fun `archive channel excludes no-login originals after their local rows have been deleted`() {
		val originalId = UUID.randomUUID().toString()
		stubArchive(
			archiveRecord(UUID.randomUUID().toString(), channel = "NAV_NO_UINNLOGGET"),
			archiveRecord(originalId, daysAgo = 2),
		)
		val request = OpprettEttersendingTestBuilder().skjemanr(formNumber).build()

		val created = api.createEttersending(request).assertHttpStatus(HttpStatus.CREATED).body.also(::trackCreated)

		assertPersonApplication(created, originalId)
	}

	@Test
	fun `only no-login originals results in a standalone Nav task`() {
		val ignored = historicalApplication(viewType = VisningsType.nologin)
		stubArchive(
			archiveRecord(ignored.innsendingsid, channel = "NAV_NO_UINNLOGGET"),
			archiveRecord(UUID.randomUUID().toString(), channel = "NAV_NO_UINNLOGGET", daysAgo = 2),
		)

		val response = api.createEttersendingsOppgave(navRequest())

		assertEquals(HttpStatus.CREATED, response.statusCode)
		val created = assertNotNull(response.body).also(::trackCreated)
		assertNavApplication(created, assertNotNull(created.innsendingsId), TokenGenerator.subject)
		assertOwner(created, ApplicationOwner.person(TokenGenerator.subject))
	}

	@Test
	fun `Nav lookup never substitutes owner or legacy identity for a missing affected person`() {
		val original = historicalApplication()
		storedApplications.setParties(original.innsendingsid, null, null)

		val response = api.createEttersendingsOppgave(navRequest())

		assertEquals(HttpStatus.CREATED, response.statusCode)
		val created = assertNotNull(response.body).also(::trackCreated)
		assertNavApplication(created, assertNotNull(created.innsendingsId), TokenGenerator.subject)
	}

	@Test
	fun `Nav duplicate warning finds active tasks by the affected person rather than the owner`() {
		val original = historicalApplication(owner = ApplicationOwner.person(otherPerson))
		val firstResponse = api.createEttersendingsOppgave(navRequest())
		assertEquals(HttpStatus.CREATED, firstResponse.statusCode)
		val first = assertNotNull(firstResponse.body).also(::trackCreated)
		assertNotification(first, original.innsendingsid, otherPerson)
		assertDuplicateWarning(navRequest(), original.innsendingsid, otherPerson)
	}

	private fun assertDuplicateWarning(request: EksternEttersendingsOppgave, originalId: String, owner: String) {
		val logger = LoggerFactory.getLogger(EttersendingService::class.java) as Logger
		val appender = ListAppender<ILoggingEvent>().apply { start() }
		logger.addAppender(appender)
		try {
			val secondResponse = api.createEttersendingsOppgave(request)
			assertEquals(HttpStatus.CREATED, secondResponse.statusCode)
			val second = assertNotNull(secondResponse.body).also(::trackCreated)
			assertNotification(second, originalId, owner)
			assertTrue(appender.list.any { it.formattedMessage == "Duplicate task for form=$formNumber, affected person already has 1 active applications" })
		} finally {
			logger.detachAppender(appender)
			appender.stop()
		}
	}

	@Test
	fun `Nav and automatic successors keep owner A and party B while B can create an independently owned successor`() {
		val ownerA = TokenGenerator.subject
		val partyB = otherPerson
		val personB = BrukerDto(partyB, BrukerDto.IdType.FNR)
		val sender = AvsenderDto(id = "987654321", idType = AvsenderDto.IdType.ORGNR)
		// Persist a historical submitted original; the owner is internal and its submission date is in the past.
		val original = historicalApplication(userId = ownerA, owner = ApplicationOwner.person(ownerA))
		storedApplications.setParties(original.innsendingsid, personB, sender)
		val tokenB = TokenGenerator(mockOAuth2Server).lagTokenXToken(partyB)
		stubPerson(partyB)
		val request = navRequest().copy(
			brukerId = partyB,
			vedleggsListe = listOf(InnsendtVedleggDto("N6", "Uploaded document"), InnsendtVedleggDto("W1", "Missing document")),
		)

		val response = api.createEttersendingsOppgave(request)
		assertEquals(HttpStatus.CREATED, response.statusCode)
		val created = assertNotNull(response.body).also(::trackCreated)
		val id = assertNotNull(created.innsendingsId)
		assertEquals(original.innsendingsid, created.ettersendingsId)
		assertOwner(created, ApplicationOwner.person(ownerA))
		assertNotification(created, original.innsendingsid, ownerA)
		api.getSoknadSendinn(id, tokenB).assertHttpStatus(HttpStatus.NOT_FOUND)
		assertTrue(api.getExistingSoknader(formNumber, authToken = tokenB).assertHttpStatus(HttpStatus.OK).body.none { it.innsendingsId == id })
		val taskList = api.oppgaveHentSoknaderForSkjemanr(formNumber, partyB, listOf(SoknadType.ettersendelse), null)
		assertEquals(HttpStatus.OK, taskList.statusCode)
		assertTrue(assertNotNull(taskList.body).any { it.innsendingsId == id })
		val wrongPersonList = api.oppgaveHentSoknaderForSkjemanr(formNumber, ownerA, null, null)
		assertEquals(HttpStatus.OK, wrongPersonList.statusCode)
		assertTrue(assertNotNull(wrongPersonList.body).none { it.innsendingsId == id })

		val accessible = api.getSoknadSendinn(id).assertHttpStatus(HttpStatus.OK).body
		val uploaded = accessible.vedleggsListe.single { it.vedleggsnr == "N6" }
		api.uploadFile(id, assertNotNull(uploaded.id)).assertHttpStatus(HttpStatus.CREATED)
		val missing = accessible.vedleggsListe.single { it.vedleggsnr == "W1" }
		api.patchVedlegg(id, assertNotNull(missing.id), PatchVedleggDto(opplastingsStatus = OpplastingsStatusDto.SendSenere))
			.assertHttpStatus(HttpStatus.OK)
		api.sendInnSoknad(id).assertHttpStatus(HttpStatus.OK)
		verify(timeout = 5000, exactly = 1) {
			receiverTarget.sendInnSoknad(match { it.innsendingsId == id }, any(), eq(sender), eq(personB), any())
		}
		val successor = api.getExistingSoknader(formNumber).assertHttpStatus(HttpStatus.OK).body.single { it.ettersendingsId == original.innsendingsid }
		trackCreated(successor)
		val successorId = assertNotNull(successor.innsendingsId)
		assertOwner(successor, ApplicationOwner.person(ownerA))
		api.getSoknadSendinn(successorId).assertHttpStatus(HttpStatus.OK)
		api.getSoknadSendinn(successorId, tokenB).assertHttpStatus(HttpStatus.NOT_FOUND)
		// Owner and stored parties are not exposed through REST; check what the automatic successor will use on submission.
		val stored = storedApplications.findBySubmissionId(successorId)
		assertEquals(personB, stored.affectedUser)
		assertEquals(sender, stored.sender)
		assertNotificationForSuccessor(successorId, original.innsendingsid, ownerA)
		verify(timeout = 5000, exactly = 1) {
			notificationTarget.avsluttBrukernotifikasjon(match { it.innsendingId == id && it.personId == ownerA })
		}
		// Wait for the submission transaction before fixture cleanup or checking final call counts.
		await().atMost(Duration.ofSeconds(5)).untilAsserted {
			assertEquals(SoknadsStatus.Innsendt, storedApplications.findBySubmissionId(id).status)
		}

		stubArchive(archiveRecord(original.innsendingsid))
		val personal = api.createEttersending(OpprettEttersendingTestBuilder().skjemanr(formNumber).build(), authToken = tokenB)
			.assertHttpStatus(HttpStatus.CREATED).body.also(::trackCreated)
		assertEquals(original.innsendingsid, personal.ettersendingsId)
		assertOwner(personal, ApplicationOwner.person(partyB))
		api.getSoknadSendinn(assertNotNull(personal.innsendingsId), tokenB).assertHttpStatus(HttpStatus.OK)
		api.getSoknadSendinn(personal.innsendingsId!!).assertHttpStatus(HttpStatus.NOT_FOUND)
		assertNotification(personal, original.innsendingsid, partyB)
	}

	private fun assertNotificationForSuccessor(id: String, originalId: String, owner: String) {
		val notification = notificationTarget.awaitNotification(id)
		assertEquals(owner, notification.soknadRef.personId)
		assertEquals(originalId, notification.soknadRef.groupId)
		assertEquals(true, notification.soknadRef.erSystemGenerert)
	}

	private fun stubPerson(ident: String, historicalIds: List<String> = emptyList()) {
		val identifiers = listOf(mapOf("ident" to ident, "gruppe" to "FOLKEREGISTERIDENT", "historisk" to false)) +
			historicalIds.map { mapOf("ident" to it, "gruppe" to "FOLKEREGISTERIDENT", "historisk" to true) }
		val response = jacksonObjectMapper().writeValueAsString(mapOf("data" to mapOf("hentIdenter" to mapOf("identer" to identifiers))))
		wireMock.stubFor(post(urlEqualTo("/pdl-api/graphql"))
			.withRequestBody(matchingJsonPath("$.operationName", equalTo("HentIdenter")))
			.withRequestBody(matchingJsonPath("$.variables.ident", equalTo(ident)))
			.willReturn(okJson(response)))
	}

	private fun historicalApplication(
		userId: String = TokenGenerator.subject,
		owner: ApplicationOwner? = ApplicationOwner.person(TokenGenerator.subject),
		viewType: VisningsType = VisningsType.fyllUt,
		submittedAt: LocalDateTime = LocalDateTime.now().minusDays(1),
	): SoknadDbData {
		// REST cannot create pre-release SQL NULL owners, past submission dates, or stored owners distinct from the user.
		return applications.save(
			SoknadDbDataTestBuilder(
				skjemanr = formNumber, brukerId = userId, status = SoknadsStatus.Innsendt,
				innsendtdato = submittedAt, visningsType = viewType, arkiveringsStatus = ArkiveringsStatus.Arkivert,
			).build().copy(
				owner = if (viewType == VisningsType.nologin) null else owner,
				affecteduser = BrukerDto(TokenGenerator.subject, BrukerDto.IdType.FNR),
			),
		).also { fixtureIds.add(it.innsendingsid) }
	}

	private fun trackCreated(created: DokumentSoknadDto) {
		fixtureIds.add(assertNotNull(created.innsendingsId))
	}

	private fun navRequest() = EksternEttersendingsOppgave(
		brukerId = TokenGenerator.subject,
		skjemanr = formNumber,
		sprak = "nb_NO",
		tema = "BID",
		tittel = "Requested documentation",
		vedleggsListe = listOf(InnsendtVedleggDto(vedleggsnr = "N6", tittel = "Supporting document")),
		koblesTilEksisterendeSoknad = true,
	)

	private fun assertPersonApplication(created: DokumentSoknadDto, originalId: String) {
		val fetched = api.getSoknadSendinn(assertNotNull(created.innsendingsId)).assertHttpStatus(HttpStatus.OK).body
		assertEquals(originalId, fetched.ettersendingsId)
		assertEquals(TokenGenerator.subject, fetched.brukerId)
		assertEquals(false, fetched.erNavOpprettet)
		assertNotification(created, originalId, TokenGenerator.subject)
	}

	private fun assertNavApplication(created: DokumentSoknadDto, originalId: String, expectedOwner: String = otherPerson) {
		val response = api.oppgaveHentSoknaderForSkjemanr(formNumber, TokenGenerator.subject, null, null)
		assertEquals(HttpStatus.OK, response.statusCode)
		val fetched = assertNotNull(response.body).single { it.innsendingsId == created.innsendingsId }
		assertEquals(originalId, fetched.ettersendingsId)
		assertEquals(TokenGenerator.subject, fetched.brukerId)
		assertEquals(true, fetched.erNavOpprettet)
		assertNotification(created, originalId, expectedOwner)
	}

	private fun assertNotification(created: DokumentSoknadDto, originalId: String, expectedOwner: String) {
		val id = assertNotNull(created.innsendingsId)
		val notification = notificationTarget.awaitNotification(id)
		assertEquals(originalId, notification.soknadRef.groupId)
		assertEquals(expectedOwner, notification.soknadRef.personId)
		verify(exactly = 1) { notificationTarget.opprettBrukernotifikasjon(match { it.soknadRef.innsendingId == id }) }
		verify(exactly = 0) { receiverTarget.sendInnSoknad(match { it.innsendingsId == id }, any(), any(), any(), any()) }
	}

	private fun assertOwner(created: DokumentSoknadDto, expected: ApplicationOwner?) {
		// Owner is not exposed through REST. Inspect only this internal field in the database.
		val stored = storedApplications.findBySubmissionId(assertNotNull(created.innsendingsId))
		assertTrue(stored.owner == expected, "The supplementary application must persist the owner selected by its creation flow")
		assertEquals(expected == null, stored.ownerIsSqlNull)
	}

	private fun stubArchive(vararg records: Map<String, Any>) {
		wireMock.stubFor(
			post(urlPathEqualTo("/saf-api/graphql")).atPriority(1).willReturn(
				aResponse().withHeader("Content-Type", "application/json").withBody(
					jacksonObjectMapper().writeValueAsString(
						mapOf("data" to mapOf("dokumentoversiktSelvbetjening" to mapOf("journalposter" to records.toList()))),
					),
				),
			),
		)
	}

	private fun archiveRecord(id: String, channel: String = "NAV_NO", daysAgo: Long = 1): Map<String, Any> = mapOf(
		"journalpostId" to id,
		"tittel" to "Historical application",
		"eksternReferanseId" to id,
		"journalstatus" to "JOURNALFOERT",
		"journalposttype" to "I",
		"tema" to "BID",
		"kanal" to channel,
		"relevanteDatoer" to listOf(mapOf("dato" to LocalDateTime.now().minusDays(daysAgo).toString(), "datotype" to "DATO_OPPRETTET")),
		"dokumenter" to listOf(mapOf("brevkode" to formNumber, "tittel" to "Historical application")),
	)
}
