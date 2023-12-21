package no.nav.soknad.innsending.rest.ekstern

import com.ninjasquad.springmockk.SpykBean
import io.mockk.slot
import io.mockk.verify
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.model.BrukernotifikasjonsType
import no.nav.soknad.innsending.utils.Api
import no.nav.soknad.innsending.utils.builders.ettersending.EksternOpprettEttersendingTestBuilder
import no.nav.soknad.innsending.utils.builders.ettersending.InnsendtVedleggDtoTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate

class EksternRestApiTest : ApplicationTest() {
	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var restTemplate: TestRestTemplate

	@SpykBean()
	lateinit var publisherInterface: PublisherInterface

	@Value("\${server.port}")
	var serverPort: Int? = 9064

	var api: Api? = null

	@BeforeEach
	fun setup() {
		api = Api(restTemplate, serverPort!!, mockOAuth2Server)
	}

	@Test
	fun `Should create ettersending with utkast brukernotifikasjon (default)`() {
		// Given
		val skjemanr = "NAV 55-00.60"
		val tema = "DAG"
		val vedleggsnr = "A1"

		val ettersending = EksternOpprettEttersendingTestBuilder()
			.skjemanr(skjemanr)
			.tema(tema)
			.vedleggsListe(listOf(InnsendtVedleggDtoTestBuilder().vedleggsnr(vedleggsnr).build()))
			.build()

		// When
		val response = api?.createEksternEttersending(ettersending)

		val message = slot<AddNotification>()
		verify(exactly = 1) { publisherInterface.opprettBrukernotifikasjon(capture(message)) }

		// Then
		assertNotNull(response?.body)

		val body = response!!.body!!
		assertEquals(skjemanr, body.skjemanr)
		assertEquals(tema, body.tema)
		assertEquals(1, body.vedleggsListe.size)
		assertEquals(vedleggsnr, body.vedleggsListe[0].vedleggsnr)

		assertEquals(false, message.captured.soknadRef.erSystemGenerert)
	}

	@Test
	fun `Should create ettersending with oppgave brukernotifikasjon`() {
		// Given
		val ettersending = EksternOpprettEttersendingTestBuilder()
			.brukernotifkasjonstype(BrukernotifikasjonsType.oppgave)
			.build()

		// When
		api?.createEksternEttersending(ettersending)

		val message = slot<AddNotification>()
		verify { publisherInterface.opprettBrukernotifikasjon(capture(message)) }

		// Then
		// The notification is an oppgave if erSystemGenerert is true
		assertEquals(true, message.captured.soknadRef.erSystemGenerert)
	}
}
