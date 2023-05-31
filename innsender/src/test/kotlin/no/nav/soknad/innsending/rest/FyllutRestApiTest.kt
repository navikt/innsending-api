package no.nav.soknad.innsending.rest

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.dto.RestErrorResponseDto
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.util.models.hoveddokument
import no.nav.soknad.innsending.util.models.hoveddokumentVariant
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.TokenGenerator
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.http.*
import org.springframework.util.LinkedMultiValueMap
import java.util.*
import kotlin.test.*


class FyllutRestApiTest : ApplicationTest() {

	@Autowired
	lateinit var restTemplate: TestRestTemplate

	@Autowired
	lateinit var soknadService: SoknadService

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server


	@Value("\${server.port}")
	var serverPort: Int? = 9064

	private val subject = "12345678901"

	private val skjemanr = "NAV 14-05.07"
	private val tittel = "Søknad om engangsstønad ved fødsel"
	private val tema = "FOR"
	private val spraak = "no_nb"

	@Test
	internal fun testOpprettSoknadPaFyllUtApi() {
		val token: String = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val skjemanr = "NAV 14-05.07"
		val tittel = "Søknad om engangsstønad ved fødsel"
		val tema = "FOR"
		val sprak = "no_nb"
		val fraFyllUt = SkjemaDto(
			brukerId = subject,
			skjemanr = skjemanr,
			tittel = tittel,
			tema = tema,
			spraak = sprak,
			hoveddokument = lagDokument(skjemanr, tittel, true, Mimetype.applicationSlashPdf),
			hoveddokumentVariant = lagDokument(skjemanr, tittel, true, Mimetype.applicationSlashJson),
			vedleggsListe = listOf(
				lagDokument(
					"T7",
					"Inntektsopplysninger for selvstendig næringsdrivende og frilansere som skal ha foreldrepenger eller svangerskapspenger",
					true,
					null,
					true,
					UUID.randomUUID().toString()
				),
				lagDokument("N6", "Dokumentasjon av veiforhold", true, null, true, UUID.randomUUID().toString())
			)
		)
		val requestEntity = HttpEntity(fraFyllUt, Hjelpemetoder.createHeaders(token))

		val response = restTemplate.exchange(
			"http://localhost:${serverPort}/fyllUt/v1/soknad", HttpMethod.POST,
			requestEntity, Unit::class.java
		)

		assertTrue(response != null)

		assertEquals(201, response.statusCodeValue)
		assertTrue(response.headers["Location"] != null)

		testHentSoknadOgSendInn(response, token)

	}

	private fun testHentSoknadOgSendInn(
		response: ResponseEntity<Unit>,
		token: String
	) {

		// Hent søknaden opprettet fra FyllUt og kjør gjennom løp for opplasting av vedlegg og innsending av søknad
		val innsendingsId = response.headers["Location"]?.first()?.substringAfterLast("/")
		assertNotNull(innsendingsId)

		val getRequestEntity = HttpEntity<Unit>(Hjelpemetoder.createHeaders(token))

		val getResponse = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${innsendingsId}", HttpMethod.GET,
			getRequestEntity, DokumentSoknadDto::class.java
		)

		Assertions.assertTrue(getResponse.body != null)
		val getSoknadDto = getResponse.body
		assertNotNull(getSoknadDto)
		assertEquals(4, getSoknadDto.vedleggsListe.size)
		assertTrue(!getSoknadDto.kanLasteOppAnnet!!)

		val vedleggT7 = getSoknadDto.vedleggsListe.first { it.vedleggsnr == "T7" }
		val patchVedleggT7 = PatchVedleggDto(null, OpplastingsStatusDto.sendesAvAndre)
		val patchRequestT7 = HttpEntity(patchVedleggT7, Hjelpemetoder.createHeaders(token))
		val patchResponseT7 = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${innsendingsId}/vedlegg/${vedleggT7.id}", HttpMethod.PATCH,
			patchRequestT7, VedleggDto::class.java
		)

		assertTrue(patchResponseT7.body != null)
		assertEquals(OpplastingsStatusDto.sendesAvAndre, patchResponseT7.body!!.opplastingsStatus)

		val vedleggN6 = getSoknadDto.vedleggsListe.first { it.vedleggsnr == "N6" }
		val patchVedleggN6 = PatchVedleggDto(null, OpplastingsStatusDto.ikkeValgt)
		val patchRequestN6 = HttpEntity(patchVedleggN6, Hjelpemetoder.createHeaders(token))
		val patchResponseN6 = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${innsendingsId}/vedlegg/${vedleggN6.id}", HttpMethod.PATCH,
			patchRequestN6, VedleggDto::class.java
		)

		assertTrue(patchResponseN6.body != null)
		assertEquals(OpplastingsStatusDto.ikkeValgt, patchResponseN6.body!!.opplastingsStatus)
		assertEquals(vedleggN6.id, patchResponseN6.body!!.id)

		val multipart = LinkedMultiValueMap<Any, Any>()
		multipart.add("file", ClassPathResource("/litenPdf.pdf"))

		val postFilRequestN6 = HttpEntity(multipart, Hjelpemetoder.createHeaders(token, MediaType.MULTIPART_FORM_DATA))
		val postFilResponseN6 = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${innsendingsId}/vedlegg/${vedleggN6.id}/fil", HttpMethod.POST,
			postFilRequestN6, FilDto::class.java
		)

		assertEquals(HttpStatus.CREATED, postFilResponseN6.statusCode)
		assertTrue(postFilResponseN6.body != null)
		assertEquals(Mimetype.applicationSlashPdf, postFilResponseN6.body!!.mimetype)

		///frontend/v1/sendInn/{innsendingsId}
		val sendInnRespons = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/sendInn/${innsendingsId}", HttpMethod.POST,
			HttpEntity<Unit>(Hjelpemetoder.createHeaders(token)), KvitteringsDto::class.java
		)

		assertTrue(sendInnRespons.statusCode == HttpStatus.OK && sendInnRespons.body != null)
		val kvitteringsDto = sendInnRespons.body
		assertEquals(1, kvitteringsDto!!.skalSendesAvAndre!!.size)
		assertTrue(kvitteringsDto.hoveddokumentRef != null)

		assertThrows<Exception> {
			restTemplate.exchange(
				"http://localhost:${serverPort}/frontend/v1/soknad/${innsendingsId}", HttpMethod.GET,
				HttpEntity<Unit>(Hjelpemetoder.createHeaders(token)), DokumentSoknadDto::class.java
			)
		}

		val hentFilURL = "http://localhost:${serverPort}/${kvitteringsDto.hoveddokumentRef}"
		val filRespons = restTemplate.exchange(
			hentFilURL, HttpMethod.GET,
			HttpEntity<Unit>(Hjelpemetoder.createHeaders(token, MediaType.APPLICATION_PDF)), ByteArray::class.java
		)
		assertEquals(HttpStatus.OK, filRespons.statusCode)
		assertTrue(filRespons.body != null)

	}

	@Test
	fun `Skal oppdatere utfylt søknad og vedlegg med nytt språk og tittel, gamle vedlegg blir slettet`() {
		// Gitt
		val token: String = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val nyttSpraak = "en_gb"
		val nyTittel = "Application for one-time grant at birth"
		val nyVedleggstittel1 = "Birth certificate"
		val nyVedleggstittel2 = "Marriage certificate"

		val dokumentSoknadDto = opprettSoknad()
		val skjemanr = dokumentSoknadDto.skjemanr
		val tema = dokumentSoknadDto.tema
		val innsendingsId = dokumentSoknadDto.innsendingsId!!

		val fraFyllUt = SkjemaDto(
			brukerId = subject,
			skjemanr = skjemanr,
			tittel = nyTittel,
			tema = tema,
			spraak = nyttSpraak,
			hoveddokument = lagDokument(
				vedleggsnr = skjemanr,
				tittel = nyTittel,
				pakrevd = true,
				mimetype = Mimetype.applicationSlashPdf,
				formioId = null
			),
			hoveddokumentVariant = lagDokument(
				vedleggsnr = skjemanr,
				tittel = nyTittel,
				pakrevd = true,
				mimetype = Mimetype.applicationSlashJson,
				formioId = null
			),
			vedleggsListe = listOf(
				lagDokument(
					"T7",
					nyVedleggstittel1,
					true,
					null,
					true,
					UUID.randomUUID().toString()
				),
				lagDokument("N6", nyVedleggstittel2, true, null, true, UUID.randomUUID().toString())
			)
		)

		val requestEntity = HttpEntity(fraFyllUt, Hjelpemetoder.createHeaders(token))

		// Når
		val response = restTemplate.exchange(
			"http://localhost:${serverPort}/fyllUt/v1/utfyltSoknad/${innsendingsId}", HttpMethod.PUT,
			requestEntity, Unit::class.java
		)
		val oppdatertSoknad = soknadService.hentSoknad(innsendingsId)


		// Så
		assertTrue(response != null)
		assertEquals(SoknadsStatusDto.utfylt, oppdatertSoknad.status, "Status er satt til utfylt")
		assertEquals(200, response.statusCodeValue)
		assertEquals(nyTittel, oppdatertSoknad.tittel)
		assertEquals("en", oppdatertSoknad.spraak, "Språk er oppdatert (blir konvertert til de første 2 bokstavene)")
		assertEquals(4, oppdatertSoknad.vedleggsListe.size, "Hoveddokument, hoveddokumentVariant og to vedlegg")
		assertFalse(
			oppdatertSoknad.vedleggsListe.any { it.vedleggsnr == "vedleggsnr1" && it.tittel == "vedleggTittel1" },
			"Skal ikke ha gammelt vedlegg"
		)
		assertFalse(
			oppdatertSoknad.vedleggsListe.any { it.vedleggsnr == "vedleggsnr2" && it.tittel == "vedleggTittel2" },
			"Skal ikke ha gammelt vedlegg"
		)
		assertTrue(
			oppdatertSoknad.vedleggsListe.any { it.vedleggsnr == "T7" && it.tittel == nyVedleggstittel1 },
			"Skal ha vedlegg T7"
		)
		assertTrue(
			oppdatertSoknad.vedleggsListe.any { it.vedleggsnr == "N6" && it.tittel == nyVedleggstittel2 },
			"Skal ha vedlegg N6"
		)
		assertEquals(oppdatertSoknad.hoveddokument!!.tittel, nyTittel, "Skal ha ny tittel på hoveddokument")
		assertEquals(oppdatertSoknad.hoveddokumentVariant!!.tittel, nyTittel, "Skal ha ny tittel på hoveddokumentVariant")

		assertEquals(null, response.body)

	}

	@Test
	fun `Skal bevare vedlegg fra send-inn, selv etter oppdatering fra fyllUt`() {
		// Gitt
		val token: String = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val dokumentSoknadDto = opprettSoknad()
		val skjemanr = dokumentSoknadDto.skjemanr
		val tema = dokumentSoknadDto.tema
		val innsendingsId = dokumentSoknadDto.innsendingsId!!

		val fyllUtVedleggstittel = "N6-ny-vedleggstittel"

		val fraFyllUt = lagSkjemaDto()
		val formioId = fraFyllUt.vedleggsListe?.find { it.vedleggsnr == "N6" }!!.formioId!!
		val oppdatertFyllUt = lagSkjemaDto(
			vedleggsListe = listOf(
				lagDokument(
					vedleggsnr = "N6",
					tittel = fyllUtVedleggstittel,
					pakrevd = false,
					mimetype = null,
					erVedlegg = true,
					formioId = formioId
				)
			)
		)

		val sendInnVedleggsTittel = "N6-fra-send-inn"
		val fraSendInn = PostVedleggDto(tittel = sendInnVedleggsTittel)

		val utfyltRequest = HttpEntity(fraFyllUt, Hjelpemetoder.createHeaders(token))
		val leggTilVedleggRequest = HttpEntity(fraSendInn, Hjelpemetoder.createHeaders(token))
		val oppdatertUtfyltRequest = HttpEntity(oppdatertFyllUt, Hjelpemetoder.createHeaders(token))

		// Når
		// Fullfører søknad i fyllUt med N6 og T1 vedlegg (de to eksisterende vedleggene vedleggsnr1 og vedleggsnr2 fjernes)
		val utfyltResponse = restTemplate.exchange(
			"http://localhost:${serverPort}/fyllUt/v1/utfyltSoknad/${innsendingsId}", HttpMethod.PUT,
			utfyltRequest, Unit::class.java
		)

		// Legger til N6 vedlegg i send-inn
		val leggTilVedleggResponse = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${innsendingsId}/vedlegg", HttpMethod.POST,
			leggTilVedleggRequest, VedleggDto::class.java
		)

		// Går tilbake til fyllUt og fjerner T1 vedlegg. Beholder N6, men endrer tittel
		val oppdatertUtfyltResponse = restTemplate.exchange(
			"http://localhost:${serverPort}/fyllUt/v1/utfyltSoknad/${innsendingsId}", HttpMethod.PUT,
			oppdatertUtfyltRequest, Unit::class.java
		)
		val oppdatertSoknad = soknadService.hentSoknad(innsendingsId)

		// Så
		assertTrue(utfyltResponse != null)
		assertTrue(leggTilVedleggResponse != null)
		assertTrue(oppdatertUtfyltResponse != null)

		assertEquals(200, utfyltResponse.statusCodeValue)
		assertEquals(201, leggTilVedleggResponse.statusCodeValue)
		assertEquals(200, oppdatertUtfyltResponse.statusCodeValue)

		assertEquals(4, oppdatertSoknad.vedleggsListe.size)

		val n6FyllUtVedlegg =
			oppdatertSoknad.vedleggsListe.find { it.vedleggsnr == "N6" && it.tittel == fyllUtVedleggstittel }
		val n6SendInnVedlegg =
			oppdatertSoknad.vedleggsListe.find { it.vedleggsnr == "N6" && it.tittel == sendInnVedleggsTittel }

		assertNotNull(n6FyllUtVedlegg!!.formioId, "Vedlegg fra fyllUt har formioId")
		assertNull(n6SendInnVedlegg!!.formioId, "Vedlegg fra sendInn har ikke formioId")

		assertFalse(
			oppdatertSoknad.vedleggsListe.any { it.vedleggsnr == "vedleggsnr1" || it.vedleggsnr == "vedleggsnr2" },
			"Skal ikke ha gammelt vedlegg"
		)
		assertFalse(
			oppdatertSoknad.vedleggsListe.any { it.vedleggsnr == "T1" },
			"Skal ikke ha gammelt vedlegg"
		)
	}

	@Test
	fun `Skal returnere error response hvis vedleggslisten ikke er tom`() {
		// Gitt
		val token: String = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val dokumentSoknadDto = opprettSoknad()
		val skjemanr = dokumentSoknadDto.skjemanr
		val tema = dokumentSoknadDto.tema
		val innsendingsId = dokumentSoknadDto.innsendingsId!!

		val fraFyllUt = SkjemaDto(
			brukerId = subject,
			skjemanr = skjemanr,
			tittel = tittel,
			tema = tema,
			spraak = spraak,
			hoveddokument = lagDokument(skjemanr, tittel, true, Mimetype.applicationSlashPdf),
			hoveddokumentVariant = lagDokument(skjemanr, tittel, true, Mimetype.applicationSlashJson),
			vedleggsListe = listOf(
				lagDokument("N6", "tittel2", true, null, true)
			)
		)

		val requestEntity = HttpEntity(fraFyllUt, Hjelpemetoder.createHeaders(token))

		// Når
		val response = restTemplate.exchange(
			"http://localhost:${serverPort}/fyllUt/v1/soknad/${innsendingsId}", HttpMethod.PUT,
			requestEntity, RestErrorResponseDto::class.java
		)
		val oppdatertSoknad = soknadService.hentSoknad(innsendingsId)

		// Så
		assertTrue(response != null)
		assertEquals(SoknadsStatusDto.opprettet, oppdatertSoknad.status, "Status er satt til opprettet")
		assertEquals(500, response.statusCodeValue)
		assertEquals("Feil antall vedlegg. Skal kun ha hoveddokument og hoveddokumentVariant", response.body!!.arsak)
		assertEquals("Innsendt vedleggsliste skal være tom", response.body!!.message)

	}

	@Test
	fun `Skal hente opprettet søknad`() {
		// Gitt
		val token: String = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val dokumentSoknadDto = opprettSoknad()
		val innsendingsId = dokumentSoknadDto.innsendingsId!!

		val requestEntity = HttpEntity(null, Hjelpemetoder.createHeaders(token))

		// Når
		val response = restTemplate.exchange(
			"http://localhost:${serverPort}/fyllUt/v1/soknad/${innsendingsId}", HttpMethod.GET,
			requestEntity, SkjemaDto::class.java
		)

		// Så
		assertTrue(response != null)
		assertEquals(200, response.statusCodeValue)
		assertEquals(skjemanr, response.body!!.skjemanr)
		assertEquals(tittel, response.body!!.tittel)
		assertEquals(2, response.body!!.vedleggsListe?.size)

		val hovedDokument = dokumentSoknadDto.vedleggsListe.find { it.erHoveddokument && !it.erVariant }
		val hovedDokumentVariant = dokumentSoknadDto.vedleggsListe.find { it.erHoveddokument && it.erVariant }

		assertEquals(hovedDokument!!.vedleggsnr, response.body!!.hoveddokument.vedleggsnr, "Hoveddokument er riktig")
		assertEquals(
			hovedDokumentVariant!!.vedleggsnr,
			response.body!!.hoveddokumentVariant.vedleggsnr,
			"HoveddokumentVariant er riktig"
		)
		assertEquals(SoknadsStatusDto.opprettet, response.body!!.status, "Status er satt til opprettet")

	}

	@Test
	fun `Skal slette opprettet søknad`() {
		// Gitt
		val token: String = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val dokumentSoknadDto = opprettSoknad()
		val innsendingsId = dokumentSoknadDto.innsendingsId!!

		val requestEntity = HttpEntity(null, Hjelpemetoder.createHeaders(token))

		// Når
		val response = restTemplate.exchange(
			"http://localhost:${serverPort}/fyllUt/v1/soknad/${innsendingsId}", HttpMethod.DELETE,
			requestEntity, BodyStatusResponseDto::class.java
		)

		// Så
		assertTrue(response != null)
		assertEquals(200, response.statusCodeValue)
		assertEquals("OK", response.body!!.status)
		assertEquals("Slettet soknad med id $innsendingsId", response.body!!.info)

		assertThrows<ResourceNotFoundException>("Søknaden skal ikke finnes") { soknadService.hentSoknad(innsendingsId) }

	}

	private fun lagSkjemaDto(vedleggsListe: List<SkjemaDokumentDto>? = null): SkjemaDto {
		return SkjemaDto(
			brukerId = subject,
			skjemanr = skjemanr,
			tittel = tittel,
			tema = tema,
			spraak = spraak,
			hoveddokument = lagDokument(
				vedleggsnr = skjemanr,
				tittel = tittel,
				pakrevd = true,
				mimetype = Mimetype.applicationSlashPdf,
				formioId = null
			),
			hoveddokumentVariant = lagDokument(
				vedleggsnr = skjemanr,
				tittel = tittel,
				pakrevd = true,
				mimetype = Mimetype.applicationSlashJson,
				formioId = null
			),
			vedleggsListe = vedleggsListe ?: listOf(
				lagDokument(
					"N6",
					"N6-vedlegg",
					false,
					null,
					true,
					UUID.randomUUID().toString()
				),
				lagDokument(
					"T1",
					"T1-vedlegg",
					false,
					null,
					true,
					UUID.randomUUID().toString()
				),
			)
		)
	}

	private fun lagDokument(
		vedleggsnr: String,
		tittel: String,
		pakrevd: Boolean,
		mimetype: Mimetype? = null,
		erVedlegg: Boolean = false,
		formioId: String? = null
	): SkjemaDokumentDto {
		return SkjemaDokumentDto(
			vedleggsnr = vedleggsnr,
			tittel = tittel,
			label = tittel,
			pakrevd = pakrevd,
			beskrivelse = "$tittel - Beskrivelse",
			mimetype = mimetype,
			document = if (erVedlegg) null else hentFil(mimetype),
			formioId = formioId
		)
	}

	// Opprett søknad med et hoveddokument, en hoveddokumentvariant og to vedlegg
	private fun opprettSoknad(): DokumentSoknadDto {
		val vedleggDtoPdf =
			Hjelpemetoder.lagVedleggDto(
				vedleggsnr = skjemanr,
				tittel = tittel,
				mimeType = Mimetype.applicationSlashPdf.value,
				fil = null,
				erVariant = false,
				erHoveddokument = true,
				formioId = null
			)
		val vedleggDtoJson =
			Hjelpemetoder.lagVedleggDto(
				vedleggsnr = skjemanr,
				tittel = tittel,
				mimeType = Mimetype.applicationSlashJson.value,
				fil = null,
				erVariant = true,
				erHoveddokument = true,
				formioId = null
			)

		val vedleggDto1 =
			Hjelpemetoder.lagVedleggDto(
				vedleggsnr = "vedleggsnr1",
				tittel = "vedleggTittel1",
				mimeType = null,
				fil = null,
				erVariant = false,
				erHoveddokument = false,
				formioId = UUID.randomUUID().toString()
			)
		val vedleggDto2 =
			Hjelpemetoder.lagVedleggDto(
				vedleggsnr = "vedleggsnr2",
				tittel = "vedleggTittel2",
				mimeType = null,
				fil = null,
				erVariant = false,
				erHoveddokument = false,
				formioId = UUID.randomUUID().toString()
			)

		val innsendingsId = soknadService.opprettNySoknad(
			Hjelpemetoder.lagDokumentSoknad(
				brukerId = TokenGenerator.subject, // Må være samme som i token (pid)
				skjemanr = skjemanr,
				spraak = spraak,
				tittel = tittel,
				tema = tema,
				vedleggsListe = listOf(vedleggDtoPdf, vedleggDtoJson, vedleggDto1, vedleggDto2),
			)
		)
		return soknadService.hentSoknad(innsendingsId)
	}

	private fun hentFil(mimetype: Mimetype?): ByteArray? =
		when (mimetype) {
			null -> null
			Mimetype.applicationSlashPdf -> Hjelpemetoder.getBytesFromFile("/litenPdf.pdf")
			Mimetype.applicationSlashJson -> Hjelpemetoder.getBytesFromFile("/__files/sanity.json")
			else -> throw RuntimeException("Testing med mimetype = $mimetype er ikke støttet")
		}

}
