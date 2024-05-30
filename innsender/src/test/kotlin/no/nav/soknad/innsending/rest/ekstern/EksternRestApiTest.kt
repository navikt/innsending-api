package no.nav.soknad.innsending.rest.ekstern

import com.ninjasquad.springmockk.SpykBean
import io.mockk.verify
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.model.BrukernotifikasjonsType
import no.nav.soknad.innsending.model.SoknadType
import no.nav.soknad.innsending.utils.Api
import no.nav.soknad.innsending.utils.Skjema
import no.nav.soknad.innsending.utils.builders.SkjemaDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.ettersending.EksternOpprettEttersendingTestBuilder
import no.nav.soknad.innsending.utils.builders.ettersending.InnsendtVedleggDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.ettersending.OpprettEttersendingTestBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
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

	val defaultSkjemanr = "NAV 02-07.05"
	val defaultTema = "FOS"
	val defaultVedleggsnr = "Y9"

	@Test
	fun `Should create ettersending with correct data`() {
		// Given
		val ettersending = EksternOpprettEttersendingTestBuilder()
			.skjemanr(defaultSkjemanr)
			.tema(defaultTema)
			.vedleggsListe(listOf(InnsendtVedleggDtoTestBuilder().vedleggsnr(defaultVedleggsnr).build()))
			.build()

		// When
		val response = api?.createEksternEttersending(ettersending)

		// Then
		assertNotNull(response?.body)

		val body = response!!.body!!
		assertEquals(defaultSkjemanr, body.skjemanr)
		assertEquals(defaultTema, body.tema)
		assertEquals(1, body.vedleggsListe.size)
		assertEquals(defaultVedleggsnr, body.vedleggsListe[0].vedleggsnr)

	}

	@Test
	fun `Should create ettersending with utkast brukernotifikasjon (default)`() {
		// Given
		val ettersending = EksternOpprettEttersendingTestBuilder()
			.skjemanr(defaultSkjemanr)
			.tema(defaultTema)
			.vedleggsListe(listOf(InnsendtVedleggDtoTestBuilder().vedleggsnr(defaultVedleggsnr).build()))
			.build()

		// When
		api?.createEksternEttersending(ettersending)

		val message = mutableListOf<AddNotification>()
		verify { publisherInterface.opprettBrukernotifikasjon(capture(message)) }

		// Then
		// The notification is an utkast if erSystemGenerert is false
		assertEquals(false, message.last().soknadRef.erSystemGenerert)
	}

	@Test
	fun `Should create ettersending with oppgave brukernotifikasjon`() {
		// Given
		val ettersending = EksternOpprettEttersendingTestBuilder()
			.skjemanr(defaultSkjemanr)
			.tema(defaultTema)
			.vedleggsListe(listOf(InnsendtVedleggDtoTestBuilder().vedleggsnr(defaultVedleggsnr).build()))
			.brukernotifikasjonstype(BrukernotifikasjonsType.oppgave) // Testing this
			.build()

		// When
		api?.createEksternEttersending(ettersending)

		val message = mutableListOf<AddNotification>()
		verify { publisherInterface.opprettBrukernotifikasjon(capture(message)) }

		// Then
		// The notification is an oppgave if erSystemGenerert is true
		assertEquals(true, message.last().soknadRef.erSystemGenerert)
	}

	@Test
	fun `Should link ettersending with existing søknad if koblesTilEksisterendeSoknad is true`() {
		// Given
		val skjemaDto = SkjemaDtoTestBuilder(skjemanr = defaultSkjemanr, tema = defaultTema).build()

		val opprettetSoknadResponse = api?.createSoknad(skjemaDto)
		val innsendingsId = opprettetSoknadResponse?.body?.innsendingsId!!

		api?.utfyltSoknad(innsendingsId, skjemaDto)
		api?.sendInnSoknad(innsendingsId)

		val ettersending = EksternOpprettEttersendingTestBuilder()
			.skjemanr(skjemaDto.skjemanr)
			.tema(defaultTema)
			.vedleggsListe(listOf(InnsendtVedleggDtoTestBuilder().vedleggsnr(defaultVedleggsnr).build()))
			.koblesTilEksisterendeSoknad(true) // Testing this
			.build()

		// When
		val response = api?.createEksternEttersending(ettersending)

		// Then
		assertNotNull(response?.body)

		val body = response!!.body!!
		assertEquals(1, body.vedleggsListe.size)
		assertEquals(innsendingsId, body.ettersendingsId, "Should have ettersendingId from existing søknad innsendingsId")
		assertEquals(defaultVedleggsnr, body.vedleggsListe[0].vedleggsnr)
	}

	@Test
	fun `Should not link ettersending with existing søknad if koblesTilEksisterendeSoknad is false (default)`() {
		// Given
		val skjemaDto = SkjemaDtoTestBuilder(skjemanr = defaultSkjemanr, tema = defaultTema).build()

		val opprettetSoknadResponse = api?.createSoknad(skjemaDto)
		val innsendingsId = opprettetSoknadResponse?.body?.innsendingsId!!

		api?.utfyltSoknad(innsendingsId, skjemaDto)
		api?.sendInnSoknad(innsendingsId)

		val ettersending = EksternOpprettEttersendingTestBuilder()
			.skjemanr(skjemaDto.skjemanr)
			.tema(skjemaDto.tema)
			.vedleggsListe(listOf(InnsendtVedleggDtoTestBuilder().vedleggsnr(defaultVedleggsnr).build()))
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
		assertEquals(defaultVedleggsnr, body.vedleggsListe[0].vedleggsnr)
	}

	@Test
	fun `Should delete ettersending`() {
		// Given
		val skjemaDto = SkjemaDtoTestBuilder(skjemanr = defaultSkjemanr, tema = defaultTema).build()

		val ettersending = EksternOpprettEttersendingTestBuilder()
			.skjemanr(skjemaDto.skjemanr)
			.tema(skjemaDto.tema)
			.vedleggsListe(listOf(InnsendtVedleggDtoTestBuilder().vedleggsnr(defaultVedleggsnr).build()))
			.build()

		val createdEttersendingResponse = api?.createEksternEttersending(ettersending)
		val innsendingsId = createdEttersendingResponse?.body?.ettersendingsId!!

		// When
		val response = api?.deleteEksternEttersending(innsendingsId)

		// Then
		assertNotNull(response?.body)

		val body = response!!.body!!
		assertEquals("Slettet ettersending med id $innsendingsId", body.info)
		assertEquals("OK", body.status)
	}

	@Test
	fun `Should return 404 if ettersending doesn't exist when deleting`() {
		// Given
		val innsendingsId = "non-existing-id"

		// When
		val response = api?.deleteEksternEttersendingFail(innsendingsId)

		// Then
		assertNotNull(response?.body)
		assertEquals(404, response?.statusCode?.value())
		val body = response!!.body!!
		assertEquals("resourceNotFound", body.errorCode)
	}

	companion object {
		@JvmStatic
		fun hentSoknaderForSkjemanr() = listOf(
			Arguments.of(null, 3), // Soknadstype not specified, should return all
			Arguments.of(listOf(SoknadType.ettersendelse), 2), // Soknadstype ettersendelse, should return 2 ettersendelser
			Arguments.of(listOf(SoknadType.soknad, SoknadType.ettersendelse), 3), // Soknadstyper søknad and ettersendelse, should return all
			Arguments.of(listOf(SoknadType.soknad), 1) // Soknadstype søknad, should return one
		)
	}

	@ParameterizedTest
	@MethodSource("hentSoknaderForSkjemanr")
	fun `Should return soknader with reqested type(s)`(
		querySoknadstyper: List<SoknadType>?,
		expectedSize: Int,
	) {
		// Given
		val skjemanr = Skjema.generateSkjemanr();
		api?.createEttersending(
			OpprettEttersendingTestBuilder()
				.skjemanr(skjemanr)
				.vedleggsListe(listOf(InnsendtVedleggDtoTestBuilder().build()))
				.build()
		)
		api?.createEttersending(
			OpprettEttersendingTestBuilder()
				.skjemanr(skjemanr)
				.vedleggsListe(listOf(InnsendtVedleggDtoTestBuilder().build()))
				.build()
		)
		api?.createSoknad(SkjemaDtoTestBuilder(skjemanr = skjemanr).build())

		// When
		val response = api?.getSoknaderForSkjemanr(skjemanr, querySoknadstyper)

		// Then
		val body = response!!.body!!
		assertEquals(expectedSize, body.size)
		if (querySoknadstyper?.isNotEmpty() == true) {
			assertTrue(body.all { querySoknadstyper.contains(it.soknadstype) })
		}
	}

}
