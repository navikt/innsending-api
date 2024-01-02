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
import no.nav.soknad.innsending.utils.builders.SkjemaDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.ettersending.EksternOpprettEttersendingTestBuilder
import no.nav.soknad.innsending.utils.builders.ettersending.InnsendtVedleggDtoTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import kotlin.test.assertNotEquals

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
	fun `Should create ettersending with correct data`() {
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

		// Then
		assertNotNull(response?.body)

		val body = response!!.body!!
		assertEquals(skjemanr, body.skjemanr)
		assertEquals(tema, body.tema)
		assertEquals(1, body.vedleggsListe.size)
		assertEquals(vedleggsnr, body.vedleggsListe[0].vedleggsnr)

	}

	@Test
	fun `Should create ettersending with utkast brukernotifikasjon (default)`() {
		// Given
		val ettersending = EksternOpprettEttersendingTestBuilder().build()

		// When
		api?.createEksternEttersending(ettersending)

		val message = slot<AddNotification>()
		verify { publisherInterface.opprettBrukernotifikasjon(capture(message)) }

		// Then
		// The notification is an utkast if erSystemGenerert is false
		assertEquals(false, message.captured.soknadRef.erSystemGenerert)
	}

	@Test
	fun `Should create ettersending with oppgave brukernotifikasjon`() {
		// Given
		val ettersending = EksternOpprettEttersendingTestBuilder()
			.brukernotifikasjonstype(BrukernotifikasjonsType.oppgave)
			.build()

		// When
		api?.createEksternEttersending(ettersending)

		val message = slot<AddNotification>()
		verify { publisherInterface.opprettBrukernotifikasjon(capture(message)) }

		// Then
		// The notification is an oppgave if erSystemGenerert is true
		assertEquals(true, message.captured.soknadRef.erSystemGenerert)
	}

	@Test
	fun `Should link ettersending with existing søknad if koblesTilEksisterendeSoknad is true`() {
		// Given
		val vedleggsnr = "A1"
		val skjemaDto = SkjemaDtoTestBuilder().build()

		val opprettetSoknadResponse = api?.createSoknad(skjemaDto)
		val innsendingsId = opprettetSoknadResponse?.body?.innsendingsId!!

		api?.utfyltSoknad(innsendingsId, skjemaDto)
		api?.sendInnSoknad(innsendingsId)

		val ettersending = EksternOpprettEttersendingTestBuilder()
			.skjemanr(skjemaDto.skjemanr)
			.koblesTilEksisterendeSoknad(true)
			.vedleggsListe(listOf(InnsendtVedleggDtoTestBuilder().vedleggsnr(vedleggsnr).build()))
			.build()

		// When
		val response = api?.createEksternEttersending(ettersending)

		// Then
		assertNotNull(response?.body)

		val body = response!!.body!!
		assertEquals(1, body.vedleggsListe.size)
		assertEquals(innsendingsId, body.ettersendingsId, "Should have ettersendingId from existing søknad innsendingsId")
		assertEquals(vedleggsnr, body.vedleggsListe[0].vedleggsnr)
	}

	@Test
	fun `Should not link ettersending with existing søknad if koblesTilEksisterendeSoknad is false (default)`() {
		// Given
		val vedleggsnr = "A1"
		val skjemaDto = SkjemaDtoTestBuilder().build()

		val opprettetSoknadResponse = api?.createSoknad(skjemaDto)
		val innsendingsId = opprettetSoknadResponse?.body?.innsendingsId!!

		api?.utfyltSoknad(innsendingsId, skjemaDto)
		api?.sendInnSoknad(innsendingsId)

		val ettersending = EksternOpprettEttersendingTestBuilder()
			.skjemanr(skjemaDto.skjemanr)
			.vedleggsListe(listOf(InnsendtVedleggDtoTestBuilder().vedleggsnr(vedleggsnr).build()))
			.build()

		// When
		val response = api?.createEksternEttersending(ettersending)

		// Then
		assertNotNull(response?.body)

		val body = response!!.body!!
		assertEquals(1, body.vedleggsListe.size)
		assertNotEquals(
			innsendingsId,
			body.ettersendingsId,
			"Should not have ettersendingId from existing søknad innsendingsId"
		)
		assertEquals(vedleggsnr, body.vedleggsListe[0].vedleggsnr)
	}
}
