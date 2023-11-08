package no.nav.soknad.innsending.rest

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.dto.RestErrorResponseDto
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.service.FilService
import no.nav.soknad.innsending.service.RepositoryUtils
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.util.models.*
import no.nav.soknad.innsending.utils.Api
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.TokenGenerator
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.SkjemaDokumentDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.SkjemaDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.VedleggDtoTestBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
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
	lateinit var repo: RepositoryUtils

	@Autowired
	lateinit var filService: FilService

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	var api: Api? = null

	@BeforeEach
	fun setup() {
		api = Api(restTemplate, serverPort!!, mockOAuth2Server)
	}

	@Value("\${server.port}")
	var serverPort: Int? = 9064


	@Test
	fun testOpprettSoknadPaFyllUtApi() {
		// Gitt
		val token: String = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val t7Vedlegg = SkjemaDokumentDtoTestBuilder(vedleggsnr = "T7").build()
		val n6Vedlegg = SkjemaDokumentDtoTestBuilder(vedleggsnr = "N6").build()

		val skjemaDto = SkjemaDtoTestBuilder(vedleggsListe = listOf(t7Vedlegg, n6Vedlegg)).build()

		// Når
		val opprettetSoknadResponse = api?.opprettSoknad(skjemaDto)

		// Så
		assertTrue(opprettetSoknadResponse != null)
		assertEquals(201, opprettetSoknadResponse.statusCode.value())
		testHentSoknadOgSendInn(opprettetSoknadResponse, token)

	}

	@Test
	fun `Skal lage kvitteringsside`() {
		// Gitt
		val skjemaDto = SkjemaDtoTestBuilder().build()

		// Når
		val opprettetSoknadResponse = api?.opprettSoknad(skjemaDto)
		val innsendingsId = opprettetSoknadResponse?.body?.innsendingsId!!

		api?.utfyltSoknad(innsendingsId, skjemaDto)
		api?.sendInnSoknad(innsendingsId)

		val soknad = soknadService.hentSoknad(opprettetSoknadResponse.body!!.innsendingsId!!)
		val vedlegg = soknad.id?.let { repo.hentAlleVedleggGittSoknadsid(it) }

		// Så
		val kvittering = vedlegg?.kvittering
		assertEquals(3, vedlegg?.size) // Hoveddokument, hoveddokumentVariant og kvittering
		assertNotNull(kvittering)
	}

	private fun testHentSoknadOgSendInn(
		response: ResponseEntity<SkjemaDto>,
		token: String
	) {

		// Hent søknaden opprettet fra FyllUt og kjør gjennom løp for opplasting av vedlegg og innsending av søknad
		val innsendingsId = response.body!!.innsendingsId
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
		assertFalse(getSoknadDto.kanLasteOppAnnet!!)

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
		val nyttSpraak = "en_gb"
		val nyTittel = "Application for one-time grant at birth"
		val nyVedleggstittel1 = "Birth certificate"
		val nyVedleggstittel2 = "Marriage certificate"

		val dokumentSoknadDto = opprettSoknad()
		val skjemanr = dokumentSoknadDto.skjemanr
		val innsendingsId = dokumentSoknadDto.innsendingsId!!

		val oppdatertT7 = SkjemaDokumentDtoTestBuilder(vedleggsnr = "T7", tittel = nyVedleggstittel1).build()
		val oppdatertN6 = SkjemaDokumentDtoTestBuilder(vedleggsnr = "N6", tittel = nyVedleggstittel2).build()
		val oppdatertHovedDokument = SkjemaDokumentDtoTestBuilder(tittel = nyTittel).asHovedDokument(skjemanr).build()
		val oppdatertHovedDokumentVariant =
			SkjemaDokumentDtoTestBuilder(tittel = nyTittel).asHovedDokumentVariant(skjemanr).build()

		val fraFyllUt = SkjemaDtoTestBuilder(
			skjemanr = dokumentSoknadDto.skjemanr,
			spraak = nyttSpraak,
			tittel = nyTittel,
			vedleggsListe = listOf(oppdatertT7, oppdatertN6),
			hoveddokument = oppdatertHovedDokument,
			hoveddokumentVariant = oppdatertHovedDokumentVariant
		).build()

		// Når
		val response = api?.utfyltSoknad(innsendingsId, fraFyllUt)
		val oppdatertSoknad = soknadService.hentSoknad(innsendingsId)

		// Så
		assertTrue(response != null)
		assertEquals(SoknadsStatusDto.utfylt, oppdatertSoknad.status, "Status er satt til utfylt")
		assertEquals(302, response.statusCode.value())
		assertTrue(response.headers["Location"] != null)
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
		assertEquals(nyTittel, oppdatertSoknad.hoveddokument!!.tittel, "Skal ha ny tittel på hoveddokument")
		assertEquals(nyTittel, oppdatertSoknad.hoveddokumentVariant!!.tittel, "Skal ha ny tittel på hoveddokumentVariant")
	}

	@Test
	fun `Skal bevare vedlegg fra send-inn, selv etter oppdatering fra fyllUt`() {
		// Gitt
		val dokumentSoknadDto = opprettSoknad() // med vedlegg vedleggsnr1 og vedleggsnr2
		val innsendingsId = dokumentSoknadDto.innsendingsId!!

		val fyllUtVedleggstittel = "N6-ny-vedleggstittel"

		val n6Vedlegg = SkjemaDokumentDtoTestBuilder(vedleggsnr = "N6").build()
		val fraFyllUt =
			SkjemaDtoTestBuilder(vedleggsListe = listOf(n6Vedlegg), skjemanr = dokumentSoknadDto.skjemanr).build()

		val formioId = fraFyllUt.vedleggsListe?.find { it.vedleggsnr == "N6" }!!.formioId!!

		val oppdatertN6Vedlegg = SkjemaDokumentDtoTestBuilder(
			vedleggsnr = "N6",
			tittel = fyllUtVedleggstittel,
			formioId = formioId
		).build()
		val oppdatertFyllUt =
			SkjemaDtoTestBuilder(vedleggsListe = listOf(oppdatertN6Vedlegg), skjemanr = dokumentSoknadDto.skjemanr).build()

		val sendInnVedleggsTittel = "N6-fra-send-inn"
		val fraSendInn = PostVedleggDto(tittel = sendInnVedleggsTittel)

		// Når
		// Fullfører søknad i fyllUt med N6 og T1 vedlegg (de to eksisterende vedleggene vedleggsnr1 og vedleggsnr2 fjernes)
		val utfyltResponse = api?.utfyltSoknad(innsendingsId, fraFyllUt)

		// Legger til N6 vedlegg i send-inn
		val leggTilVedleggResponse = api?.leggTilVedlegg(innsendingsId, fraSendInn)

		// Går tilbake til fyllUt og fjerner T1 vedlegg. Beholder N6, men endrer tittel
		val oppdatertUtfyltResponse = api?.utfyltSoknad(innsendingsId, oppdatertFyllUt)
		val oppdatertSoknad = soknadService.hentSoknad(innsendingsId)

		// Så
		assertTrue(utfyltResponse != null)
		assertTrue(leggTilVedleggResponse != null)
		assertTrue(oppdatertUtfyltResponse != null)

		assertEquals(302, utfyltResponse.statusCode.value())
		assertEquals(201, leggTilVedleggResponse.statusCode.value())
		assertEquals(302, oppdatertUtfyltResponse.statusCode.value())

		assertEquals(4, oppdatertSoknad.vedleggsListe.size)

		assertNotNull(utfyltResponse.headers["Location"])
		assertNotNull(oppdatertUtfyltResponse.headers["Location"])

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
	fun `Skal lagre filene i databasen`() {
		// Gitt
		val dokumentSoknadDto = opprettSoknad()
		val innsendingsId = dokumentSoknadDto.innsendingsId!!

		val fraFyllUt = SkjemaDtoTestBuilder(skjemanr = dokumentSoknadDto.skjemanr).build()

		// Når
		api?.oppdaterSoknad(innsendingsId, fraFyllUt)
		val oppdatertSoknad = soknadService.hentSoknad(innsendingsId)

		val filer = oppdatertSoknad.vedleggsListe.flatMap {
			filService.hentFiler(
				oppdatertSoknad, innsendingsId,
				it.id!!, true
			)
		}

		// Så
		assertEquals(2, filer.size)
		filer.forEach {
			assertTrue(it.data!!.isNotEmpty())
		}
		filer.find { it.mimetype == Mimetype.applicationSlashPdf }?.let {
			assertTrue(it.filnavn!!.endsWith(".pdf"), "Skal være pdf-fil")
		}

		filer.find { it.mimetype == Mimetype.applicationSlashJson }?.let {
			assertTrue(it.filnavn!!.endsWith(".json"), "Skal være json-fil")
		}
	}

	@Test
	fun `Skal returnere riktig felter ved oppdatering av søknad`() {
		// Gitt
		val dokumentSoknadDto = opprettSoknad()
		val innsendingsId = dokumentSoknadDto.innsendingsId!!
		val nyttSpraak = "en_gb"

		val fraFyllUt = SkjemaDtoTestBuilder(skjemanr = dokumentSoknadDto.skjemanr, spraak = nyttSpraak).build()

		// Når
		val response = api?.oppdaterSoknad(innsendingsId, fraFyllUt)
		val oppdatertSoknad = response?.body!!

		// Så
		assertEquals(dokumentSoknadDto.skjemanr, oppdatertSoknad.skjemanr)
		assertEquals(dokumentSoknadDto.innsendingsId, oppdatertSoknad.innsendingsId)
		assertEquals(dokumentSoknadDto.brukerId, oppdatertSoknad.brukerId)

		assertEquals("en", oppdatertSoknad.spraak, "Språk er oppdatert")
		assertNotEquals(dokumentSoknadDto.spraak, oppdatertSoknad.spraak, "Språk er oppdatert")

		val tidligereEndretDatoEpoch = dokumentSoknadDto.endretDato!!.toEpochSecond()
		val oppdatertEndretDatoEpoch = oppdatertSoknad.endretDato!!.toEpochSecond()

		assertTrue(dokumentSoknadDto.endretDato!!.isBefore(oppdatertSoknad.endretDato), "Skal returnere nyere endretDato")
		assertTrue(
			tidligereEndretDatoEpoch <= oppdatertEndretDatoEpoch && tidligereEndretDatoEpoch + 10 >= oppdatertEndretDatoEpoch,
			"Endret dato skal være innenfor 10 sekunder fra opprettelse i denne testen"
		)

	}

	@Test
	fun `Skal returnere error response hvis vedleggslisten ikke er tom`() {
		// Gitt
		val token: String = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val dokumentSoknadDto = opprettSoknad()
		val innsendingsId = dokumentSoknadDto.innsendingsId!!

		val vedlegg = SkjemaDokumentDtoTestBuilder().build()
		val fraFyllUt = SkjemaDtoTestBuilder(vedleggsListe = listOf(vedlegg)).build()

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
		assertEquals(500, response.statusCode.value())
		assertEquals(
			"Feil antall vedlegg. Skal kun ha hoveddokument og hoveddokumentVariant. Innsendt vedleggsliste skal være tom",
			response.body!!.message
		)

	}

	@Test
	fun `Skal hente opprettet søknad`() {
		// Gitt
		val skjemanr = "NAV 11-12.12"
		val dokumentSoknadDto = opprettSoknad(skjemanr)
		val innsendingsId = dokumentSoknadDto.innsendingsId!!

		// Når
		val response = api?.hentSoknad(innsendingsId)

		val opprettetSoknad = response?.body!!

		// Så
		assertTrue(response != null)
		assertEquals(200, response.statusCode.value())
		assertEquals(skjemanr, opprettetSoknad.skjemanr)
		assertEquals(2, opprettetSoknad.vedleggsListe?.size)
		assertNotNull(opprettetSoknad.hoveddokumentVariant.document, "HoveddokumentVariant skal ha fil")
		assertNull(opprettetSoknad.hoveddokument.document, "Hoveddokument skal ikke ha fil")

		val hovedDokument = dokumentSoknadDto.vedleggsListe.hovedDokument
		val hovedDokumentVariant = dokumentSoknadDto.vedleggsListe.hovedDokumentVariant

		assertEquals(hovedDokument!!.vedleggsnr, opprettetSoknad.hoveddokument.vedleggsnr, "Hoveddokument er riktig")
		assertEquals(
			hovedDokumentVariant!!.vedleggsnr,
			opprettetSoknad.hoveddokumentVariant.vedleggsnr,
			"HoveddokumentVariant er riktig"
		)
		assertNotNull(opprettetSoknad.endretDato)
		assertEquals(SoknadsStatusDto.opprettet, opprettetSoknad.status, "Status er satt til opprettet")
	}

	@Test
	fun `Skal slette opprettet søknad`() {
		// Gitt
		val dokumentSoknadDto = opprettSoknad()
		val innsendingsId = dokumentSoknadDto.innsendingsId!!

		// Når
		val response = api?.slettSoknad(innsendingsId)

		// Så
		assertTrue(response != null)
		assertEquals(200, response.statusCode.value())
		assertEquals("OK", response.body!!.status)
		assertEquals("Slettet soknad med id $innsendingsId", response.body!!.info)

		assertThrows<ResourceNotFoundException>("Søknaden skal ikke finnes") { soknadService.hentSoknad(innsendingsId) }
	}

	@Test
	fun `Skal redirecte ved eksisterende søknad gitt at opprettNySoknad er false`() {
		// Gitt
		val dokumentSoknadDto = opprettSoknad(skjemanr = "NAV-redirect")

		val fraFyllUt = SkjemaDtoTestBuilder(skjemanr = dokumentSoknadDto.skjemanr).build()

		// Når
		val response = api?.opprettSoknad(fraFyllUt, false)

		// Så
		assertTrue(response != null)
		assertEquals(302, response.statusCode.value())
		assertEquals(
			"http://localhost:3001/fyllut/${fraFyllUt.skjemapath}/paabegynt?sub=digital",
			response.headers.location!!.toString()
		)
	}

	@Test
	fun `Skal opprette søknad når opprettNySoknad er true, selv om brukeren har en søknad med samme skjemanr`() {
		// Gitt
		val dokumentSoknadDto = opprettSoknad()
		val innsendingsId = dokumentSoknadDto.innsendingsId!!

		val fraFyllUt = SkjemaDtoTestBuilder(skjemanr = dokumentSoknadDto.skjemanr).build()

		// Når
		val response = api?.opprettSoknad(fraFyllUt, true)

		// Så
		assertTrue(response != null)
		assertEquals(201, response.statusCode.value())
		assertNotEquals(response.body?.innsendingsId, innsendingsId, "Forventer ny innsendingsId")
	}

	@Test
	fun `Should return correct prefill-data from PDL`() {
		// Given
		val properties = "sokerFornavn,sokerEtternavn"

		// When
		val response = api?.getPrefillData(properties)

		// Then
		assertTrue(response != null)
		assertEquals(200, response.statusCode.value())
		assertEquals("Ola", response.body?.sokerFornavn)
		assertEquals("Nordmann", response.body?.sokerEtternavn)
	}

	@Test
	fun `Should return correct prefill-data from Arena (maalgrupper)`() {
		// Given
		val properties = "sokerMaalgrupper"

		// When
		val response = api?.getPrefillData(properties)

		// Then
		assertTrue(response != null)
		assertEquals(200, response.statusCode.value())
		assertEquals(1, response.body?.sokerMaalgrupper?.size)
		assertEquals("NEDSARBEVN", response.body?.sokerMaalgrupper?.get(0)?.maalgruppetype?.name)
		assertEquals("2023-01-01", response.body?.sokerMaalgrupper?.get(0)?.gyldighetsperiode?.fom.toString())
		assertEquals("Person med nedsatt arbeidsevne pga. sykdom", response.body?.sokerMaalgrupper?.get(0)?.maalgruppenavn)
	}

	@Test
	fun `Should return correct prefill-data from Arena (aktiviteter)`() {
		// Given
		val properties = "sokerAktiviteter"

		// When
		val response = api?.getPrefillData(properties)

		// Then
		assertTrue(response != null)
		assertEquals(200, response.statusCode.value())
		assertEquals(1, response.body?.sokerAktiviteter?.size)
		assertEquals("ARBTREN", response.body?.sokerAktiviteter?.get(0)?.aktivitetstype)
		assertEquals("Arbeidstrening", response.body?.sokerAktiviteter?.get(0)?.aktivitetsnavn)
	}

	@Test
	fun `Should return 400 from prefill-data if invalid prop is sent`() {
		// Given
		val properties = "sokerFornavn,sokerEtternavn,sokerInvalid"

		// When
		val response = api?.getPrefillDataFail(properties)

		// Then
		assertTrue(response != null)
		assertEquals(400, response.statusCode.value())
		assertEquals("'sokerInvalid' not a valid property", response.body?.message)
	}


	// Opprett søknad med et hoveddokument, en hoveddokumentvariant og to vedlegg
	private fun opprettSoknad(skjemanr: String = "NAV 08-21.05"): DokumentSoknadDto {
		val vedleggDtoPdf = VedleggDtoTestBuilder(vedleggsnr = skjemanr).asHovedDokument().build()
		val vedleggDtoJson = VedleggDtoTestBuilder(vedleggsnr = skjemanr).asHovedDokumentVariant().build()
		val vedleggDto1 = VedleggDtoTestBuilder(
			erHoveddokument = false,
			vedleggsnr = "vedleggsnr1",
			tittel = "vedleggTittel1",
			formioId = UUID.randomUUID().toString()
		).build()
		val vedleggDto2 = VedleggDtoTestBuilder(
			erHoveddokument = false,
			vedleggsnr = "vedleggsnr2",
			tittel = "vedleggTittel2",
			formioId = UUID.randomUUID().toString()
		).build()

		val vedleggsListe = listOf(vedleggDtoPdf, vedleggDtoJson, vedleggDto1, vedleggDto2)

		val dokumentSoknadDto =
			DokumentSoknadDtoTestBuilder(
				brukerId = TokenGenerator.subject,
				skjemanr = skjemanr,
				vedleggsListe = vedleggsListe
			).build()

		val innsendingsId = soknadService.opprettNySoknad(dokumentSoknadDto).innsendingsId!!

		return soknadService.hentSoknad(innsendingsId)
	}

}
