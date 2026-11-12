package no.nav.soknad.innsending.rest

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.ninjasquad.springmockk.MockkSpyBean
import io.mockk.clearMocks
import io.mockk.verify
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerAPITest
import no.nav.soknad.innsending.model.DokumentSoknadDto
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
	}

	@AfterEach
	fun removeHistoricalFixtures() {
		storedApplications.deleteApplications(fixtureIds)
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

	@ParameterizedTest(name = "source owner present: {0}")
	@ValueSource(booleans = [true, false])
	fun `Nav initiated archived supplementary application inherits the stored owner even when it differs from the request user`(ownerPresent: Boolean) {
		val owner = if (ownerPresent) ApplicationOwner.person(otherPerson) else null
		val original = historicalApplication(userId = otherPerson, owner = owner)
		stubArchive(archiveRecord(original.innsendingsid))

		val response = api.createEttersendingsOppgave(navRequest())

		assertEquals(HttpStatus.CREATED, response.statusCode)
		val created = assertNotNull(response.body).also(::trackCreated)
		assertNavApplication(created, original.innsendingsid)
		assertOwner(created, owner)
	}

	@Test
	fun `local no-login original is skipped in favour of an older authenticated original with SQL null owner`() {
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
		assertNavApplication(created, assertNotNull(created.innsendingsId))
		assertOwner(created, ApplicationOwner.person(TokenGenerator.subject))
	}

	private fun historicalApplication(
		userId: String = TokenGenerator.subject,
		owner: ApplicationOwner? = null,
		viewType: VisningsType = VisningsType.fyllUt,
		submittedAt: LocalDateTime = LocalDateTime.now().minusDays(1),
	): SoknadDbData {
		// REST cannot create pre-release SQL NULL owners, past submission dates, or stored owners distinct from the user.
		return applications.save(
			SoknadDbDataTestBuilder(
				skjemanr = formNumber, brukerId = userId, status = SoknadsStatus.Innsendt,
				innsendtdato = submittedAt, visningsType = viewType, arkiveringsStatus = ArkiveringsStatus.Arkivert,
			).build().copy(owner = owner),
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
		assertNotification(created, originalId)
	}

	private fun assertNavApplication(created: DokumentSoknadDto, originalId: String) {
		val response = api.oppgaveHentSoknaderForSkjemanr(formNumber, TokenGenerator.subject, null, null)
		assertEquals(HttpStatus.OK, response.statusCode)
		val fetched = assertNotNull(response.body).single { it.innsendingsId == created.innsendingsId }
		assertEquals(originalId, fetched.ettersendingsId)
		assertEquals(TokenGenerator.subject, fetched.brukerId)
		assertEquals(true, fetched.erNavOpprettet)
		assertNotification(created, originalId)
	}

	private fun assertNotification(created: DokumentSoknadDto, originalId: String) {
		val id = assertNotNull(created.innsendingsId)
		val notification = notificationTarget.awaitNotification(id)
		assertEquals(originalId, notification.soknadRef.groupId)
		assertEquals(TokenGenerator.subject, notification.soknadRef.personId, "Release one must retain the legacy notification recipient")
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
