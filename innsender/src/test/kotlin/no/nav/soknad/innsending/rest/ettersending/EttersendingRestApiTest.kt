package no.nav.soknad.innsending.rest.ettersending

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.utils.Api
import no.nav.soknad.innsending.utils.builders.SkjemaDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.ettersending.InnsendtVedleggDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.ettersending.OpprettEttersendingTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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

	@Value("\${server.port}")
	var serverPort: Int? = 9064

	var api: Api? = null

	@BeforeEach
	fun setup() {
		api = Api(restTemplate, serverPort!!, mockOAuth2Server)
	}

	@Test
	fun `Should create ettersending with no existing søknader`() {
		// Given
		val skjemanr = "NAV 55-00.60"
		val tema = "DAG"
		val vedleggsnr = "A1"

		val ettersending = OpprettEttersendingTestBuilder()
			.skjemanr(skjemanr)
			.tema(tema)
			.vedleggsListe(listOf(InnsendtVedleggDtoTestBuilder().vedleggsnr(vedleggsnr).build()))
			.build()

		// When
		val response = api?.createEttersending(ettersending)

		// Then
		assertNotNull(response?.body)

		val body = response!!.body!!
		assertEquals(skjemanr, body.skjemanr)
		assertEquals(tema, body.tema)
		assertEquals(1, body.vedleggsListe.size)
		assertEquals(vedleggsnr, body.vedleggsListe[0].vedleggsnr)
	}

	@Test
	fun `Should create ettersending with existing søknad`() {
		// Given
		val vedleggsnr = "A1"
		val skjemaDto = SkjemaDtoTestBuilder().build()

		val opprettetSoknadResponse = api?.createSoknad(skjemaDto)
		val innsendingsId = opprettetSoknadResponse?.body?.innsendingsId!!

		api?.utfyltSoknad(innsendingsId, skjemaDto)
		api?.sendInnSoknad(innsendingsId)

		val ettersending = OpprettEttersendingTestBuilder()
			.skjemanr(skjemaDto.skjemanr)
			.vedleggsListe(listOf(InnsendtVedleggDtoTestBuilder().vedleggsnr(vedleggsnr).build()))
			.build()

		// When
		val response = api?.createEttersending(ettersending)

		// Then
		assertNotNull(response?.body)

		val body = response!!.body!!
		assertEquals(1, body.vedleggsListe.size)
		assertEquals(innsendingsId, body.ettersendingsId, "Should have ettersendingId from existing søknad innsendingsId")
		assertEquals(vedleggsnr, body.vedleggsListe[0].vedleggsnr)
	}

}
