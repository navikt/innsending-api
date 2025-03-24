package no.nav.soknad.innsending.rest.ettersending

import com.ninjasquad.springmockk.SpykBean
import io.mockk.clearAllMocks
import io.mockk.slot
import io.mockk.verify
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.arkivering.soknadsmottaker.model.Varsel
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.utils.Api
import no.nav.soknad.innsending.utils.builders.SkjemaDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.ettersending.InnsendtVedleggDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.ettersending.OpprettEttersendingTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate

class EttersendingRestApiTest : ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var restTemplate: TestRestTemplate

	@SpykBean
	lateinit var notificationPublisher: PublisherInterface

	@Value("\${server.port}")
	var serverPort: Int? = 9064

	var api: Api? = null

	@BeforeEach
	fun setup() {
		api = Api(restTemplate, serverPort!!, mockOAuth2Server)
		clearAllMocks()
	}

	@Test
	fun `Should create ettersending with no existing søknader`() {
		// Given
		val skjemanr = "NAV 55-00.60"
		val tema = "DAG"
		val vedleggsnr = "A1"

		val opprettEttersendingRequest = OpprettEttersendingTestBuilder()
			.skjemanr(skjemanr)
			.tema(tema)
			.vedleggsListe(listOf(InnsendtVedleggDtoTestBuilder().vedleggsnr(vedleggsnr).build()))
			.build()

		// When
		val ettersending = api!!.createEttersending(opprettEttersendingRequest)
			.assertSuccess()
			.body

		// Then
		assertEquals(skjemanr, ettersending.skjemanr)
		assertEquals(tema, ettersending.tema)
		assertEquals(1, ettersending.vedleggsListe.size)
		assertEquals(vedleggsnr, ettersending.vedleggsListe[0].vedleggsnr)

		val notificationSlot = slot<AddNotification>()
		verify(exactly = 1) { notificationPublisher.opprettBrukernotifikasjon(capture(notificationSlot)) }
		val notification = notificationSlot.captured

		assertEquals(ettersending.innsendingsId, notification.soknadRef.innsendingId)
		assertEquals(ettersending.innsendingsId, notification.soknadRef.groupId)
		assertEquals(ettersending.brukerId, notification.soknadRef.personId)
		assertEquals(false, notification.soknadRef.erSystemGenerert)
		assertEquals(true, notification.soknadRef.erEttersendelse)
		assertEquals("Ettersend manglende vedlegg til: ${ettersending.tittel}", notification.brukernotifikasjonInfo.notifikasjonsTittel)
		assertEquals(28, notification.brukernotifikasjonInfo.antallAktiveDager)
		assertNotNull(notification.brukernotifikasjonInfo.lenke)
		assertEquals(1, notification.brukernotifikasjonInfo.eksternVarsling.size)
		assertTrue(notification.brukernotifikasjonInfo.eksternVarsling.any { it.kanal === Varsel.Kanal.sms })
		assertNull(notification.brukernotifikasjonInfo.utsettSendingTil)
	}

	@Test
	fun `Should create ettersending with existing søknad`() {
		// Given
		val vedleggsnr = "A1"
		val skjemaDto = SkjemaDtoTestBuilder().build()

		val opprettetSoknad = api!!.createSoknad(skjemaDto)
			.assertSuccess()
			.body
		val innsendingsId = opprettetSoknad.innsendingsId!!

		api?.utfyltSoknad(innsendingsId, skjemaDto)
		val sendInnSoknadResponse = api?.sendInnSoknad(innsendingsId)
		val innsendtSoknad = sendInnSoknadResponse!!.body!!

		val ettersending = OpprettEttersendingTestBuilder()
			.skjemanr(skjemaDto.skjemanr)
			.vedleggsListe(listOf(InnsendtVedleggDtoTestBuilder().vedleggsnr(vedleggsnr).build()))
			.build()

		// When
		val manueltOpprettetEttersending = api!!.createEttersending(ettersending)
			.assertSuccess()
			.body

		// Then
		assertEquals(1, manueltOpprettetEttersending.vedleggsListe.size)
		assertEquals(innsendingsId, manueltOpprettetEttersending.ettersendingsId, "Should have ettersendingId from existing søknad innsendingsId")
		assertEquals(vedleggsnr, manueltOpprettetEttersending.vedleggsListe[0].vedleggsnr)

		val notifications = mutableListOf<AddNotification>()
		verify(exactly = 2) { notificationPublisher.opprettBrukernotifikasjon(capture(notifications)) }
		val lastNotification = notifications.last()

		assertEquals(manueltOpprettetEttersending.innsendingsId, lastNotification.soknadRef.innsendingId)
		assertEquals(innsendtSoknad.innsendingsId, lastNotification.soknadRef.groupId)
		assertEquals(manueltOpprettetEttersending.brukerId, lastNotification.soknadRef.personId)
		assertEquals(false, lastNotification.soknadRef.erSystemGenerert)
		assertEquals(true, lastNotification.soknadRef.erEttersendelse)
		assertEquals("Ettersend manglende vedlegg til: ${ettersending.tittel}", lastNotification.brukernotifikasjonInfo.notifikasjonsTittel)
		assertEquals(28, lastNotification.brukernotifikasjonInfo.antallAktiveDager)
		assertNotNull(lastNotification.brukernotifikasjonInfo.lenke)
		assertEquals(1, lastNotification.brukernotifikasjonInfo.eksternVarsling.size)
		assertTrue(lastNotification.brukernotifikasjonInfo.eksternVarsling.any { it.kanal === Varsel.Kanal.sms })
		assertNull(lastNotification.brukernotifikasjonInfo.utsettSendingTil)
	}

}
