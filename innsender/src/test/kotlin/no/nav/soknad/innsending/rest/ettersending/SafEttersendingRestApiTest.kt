package no.nav.soknad.innsending.rest.ettersending

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.ninjasquad.springmockk.SpykBean
import io.mockk.clearMocks
import io.mockk.verify
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.utils.ApiWebClient
import no.nav.soknad.innsending.utils.builders.ettersending.OpprettEttersendingTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.util.UUID

class SafEttersendingRestApiTest : ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@SpykBean
	lateinit var notificationPublisher: PublisherInterface

	@LocalServerPort
	var serverPort: Int = 0

	private lateinit var api: ApiWebClient

	@BeforeEach
	fun setup() {
		api = ApiWebClient(webTestClient, serverPort, mockOAuth2Server)
		clearMocks(notificationPublisher)
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

		wireMock.verify(1, postRequestedFor(urlPathEqualTo("/saf-api/graphql")))
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
