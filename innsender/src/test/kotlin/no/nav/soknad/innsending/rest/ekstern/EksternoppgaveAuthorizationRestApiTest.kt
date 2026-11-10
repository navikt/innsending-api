package no.nav.soknad.innsending.rest.ekstern

import com.ninjasquad.springmockk.MockkSpyBean
import io.mockk.clearAllMocks
import io.mockk.verify
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.EksternEttersendingsOppgave
import no.nav.soknad.innsending.model.InnsendtVedleggDto
import no.nav.soknad.innsending.model.SoknadType
import no.nav.soknad.innsending.util.testpersonid
import no.nav.soknad.innsending.utils.ApiWebClient
import no.nav.soknad.innsending.utils.TokenGenerator
import no.nav.soknad.innsending.utils.ApplicationTestRepository
import org.springframework.context.annotation.Import
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Import(ApplicationTestRepository::class)
class EksternoppgaveAuthorizationRestApiTest : ApplicationTest() {
	@Autowired lateinit var storedApplications: ApplicationTestRepository
	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@MockkSpyBean
	lateinit var publisher: PublisherInterface

	@LocalServerPort
	var serverPort: Int = 0

	private lateinit var api: ApiWebClient
	private val tokens get() = TokenGenerator(mockOAuth2Server)
	private val task = EksternEttersendingsOppgave(
		brukerId = testpersonid,
		skjemanr = "NAV 55-00.60",
		sprak = "nb_NO",
		tema = "BID",
		tittel = "Test task",
		koblesTilEksisterendeSoknad = false,
		vedleggsListe = listOf(InnsendtVedleggDto(vedleggsnr = "W1", tittel = "Test attachment")),
	)

	@BeforeEach
	fun setup() {
		api = ApiWebClient(webTestClient, serverPort, mockOAuth2Server)
		clearAllMocks()
	}

	@Test
	fun `should create a task for an authorized Azure application and return it through task listing`() {
		val response = api.createEttersendingsOppgave(task, token = tokens.taskApplicationToken(additionalClaims = mapOf("client_id" to "different-application")))
		assertEquals(HttpStatus.CREATED, response.statusCode)
		val created = assertNotNull(response.body)
		val id = assertNotNull(created.innsendingsId)
		assertEquals(task.brukerId, created.brukerId)
		assertEquals(task.skjemanr, created.skjemanr)
		assertEquals(true, created.erNavOpprettet)
		assertEquals(TokenGenerator.TASK_CLIENT_ID, created.applikasjon)

		val stored = tasks().single { it.innsendingsId == id }
		assertEquals(task.brukerId, stored.brukerId)
		verify(exactly = 1, timeout = 5000) {
			publisher.opprettBrukernotifikasjon(match { it.soknadRef.innsendingId == id })
		}
	}

	@Test
	fun `should deny task creation without azp even when client_id names an authorized caller`() {
		assertCreationDenied(tokens.taskApplicationToken(azp = null, additionalClaims = mapOf("client_id" to TokenGenerator.TASK_CLIENT_ID)))
	}

	@Test
	fun `authorized applications cannot assign a malformed person identity`() {
		val existingIds = tasks().map { it.innsendingsId }.toSet()
		// Invalid owner rows cannot be observed through person/task listings, which deliberately omit them.
		val countBefore = storedApplications.countByLegacyUserId("invalid-identity")
		val response = api.createEttersendingsOppgaveFail(task.copy(brukerId = "invalid-identity"), tokens.taskApplicationToken())
		assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
		assertEquals("illegalArgument", assertNotNull(response.body).errorCode)
		assertEquals(existingIds, tasks().map { it.innsendingsId }.toSet())
		assertEquals(countBefore, storedApplications.countByLegacyUserId("invalid-identity"))
		verify(exactly = 0) { publisher.opprettBrukernotifikasjon(any()) }
	}

	@Test
	fun `should deny task creation for unknown azp`() {
		assertCreationDenied(tokens.taskApplicationToken(azp = "unknown-client-id"))
	}

	@Test
	fun `should deny task creation for a preauthorized application without task rights`() {
		assertCreationDenied(tokens.taskApplicationToken(azp = "other-client-id"))
	}

	@Test
	fun `should deny task creation for a user token from an otherwise authorized application`() {
		assertCreationDenied(tokens.taskApplicationToken(objectId = "different-user-object-id"))
	}

	@Test
	fun `should deny task creation without oid`() {
		assertCreationDenied(tokens.taskApplicationToken(objectId = null))
	}

	@Test
	fun `should deny task creation for a non-string azp`() {
		assertCreationDenied(tokens.taskApplicationToken(azp = listOf(TokenGenerator.TASK_CLIENT_ID)))
	}

	@Test
	fun `should deny task deletion before changing the application or closing its notification`() {
		val created = api.createEttersendingsOppgave(task).body
		val id = assertNotNull(assertNotNull(created).innsendingsId)
		verify(exactly = 1, timeout = 5000) {
			publisher.opprettBrukernotifikasjon(match { it.soknadRef.innsendingId == id })
		}

		val response = api.eksternOppgaveSlettFail(id, token = tokens.taskApplicationToken(azp = "other-client-id"))
		assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
		assertEquals("errorCode.forbidden", assertNotNull(response.body).errorCode)
		assertTrue(tasks().any { it.innsendingsId == id })
		verify(exactly = 0) { publisher.avsluttBrukernotifikasjon(match { it.innsendingId == id }) }
	}

	@Test
	fun `should deny task listing for a preauthorized application without task rights`() {
		val response = api.oppgaveHentSoknaderForSkjemanrFail(
			task.skjemanr,
			task.brukerId,
			listOf(SoknadType.ettersendelse),
			null,
			token = tokens.taskApplicationToken(azp = "other-client-id"),
		)
		assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
		assertEquals("errorCode.forbidden", assertNotNull(response.body).errorCode)
	}

	private fun assertCreationDenied(token: String) {
		val existingIds = tasks().map { it.innsendingsId }.toSet()
		val response = api.createEttersendingsOppgaveFail(task, token = token)
		assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
		assertEquals("errorCode.forbidden", assertNotNull(response.body).errorCode)
		assertEquals(existingIds, tasks().map { it.innsendingsId }.toSet())
		verify(exactly = 0) { publisher.opprettBrukernotifikasjon(any()) }
	}

	private fun tasks(): List<DokumentSoknadDto> {
		val response = api.oppgaveHentSoknaderForSkjemanr(task.skjemanr, task.brukerId, listOf(SoknadType.ettersendelse), null)
		assertEquals(HttpStatus.OK, response.statusCode)
		return assertNotNull(response.body)
	}
}
