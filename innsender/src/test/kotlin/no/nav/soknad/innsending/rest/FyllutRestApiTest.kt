package no.nav.soknad.innsending.rest

import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.soknad.innsending.InnsendingApiApplication
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.TokenGenerator
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.core.io.ClassPathResource
import org.springframework.http.*
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.util.LinkedMultiValueMap
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


@ActiveProfiles("test")
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
	properties = ["spring.main.allow-bean-definition-overriding=true"],
	classes = [InnsendingApiApplication::class]
)
@ExtendWith(SpringExtension::class)
@AutoConfigureWireMock
@EnableMockOAuth2Server(port = 9898)
class FyllutRestApiTest {

	@Autowired
	lateinit var restTemplate: TestRestTemplate

	@Autowired
	lateinit var soknadService: SoknadService

	@Autowired
	lateinit var tokenGenerator: TokenGenerator


	@Value("\${server.port}")
	var serverPort: Int? = 9064

	private val subject = "12345678901"

	@Test
	internal fun testOpprettSoknadPaFyllUtApi() {
		val token: String = tokenGenerator.lagTokenXToken()

		val skjemanr = "NAV 14-05.07"
		val tittel = "Søknad om engangsstønad ved fødsel"
		val tema = "FOR"
		val sprak = "no_nb"
		val fraFyllUt = SkjemaDto(
			subject, skjemanr, tittel, tema, sprak,
			lagDokument(skjemanr, tittel, true, Mimetype.applicationSlashPdf),
			lagDokument(skjemanr, tittel, true, Mimetype.applicationSlashJson),
			listOf(
				lagDokument(
					"T7",
					"Inntektsopplysninger for selvstendig næringsdrivende og frilansere som skal ha foreldrepenger eller svangerskapspenger",
					true,
					null,
					true
				),
				lagDokument("N6", "Dokumentasjon av veiforhold", true, null, true)
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
	fun `Skal oppdatere søknad med nytt språk og tittel`() {
		// Gitt
		val token: String = tokenGenerator.lagTokenXToken()

		val skjemanr = "NAV 14-05.07"
		val tittel = "Søknad om engangsstønad ved fødsel"
		val tema = "FOR"
		val spraak = "no_nb"

		val nyttSpraak = "en_gb"
		val nyTittel = "Application for one-time grant at birth"

		val vedleggDto1 =
			Hjelpemetoder.lagVedleggDto(vedleggsnr = "vedleggsnr1", tittel = "vedleggTittel1", mimeType = null, fil = null)
		val vedleggDto2 =
			Hjelpemetoder.lagVedleggDto(vedleggsnr = "vedleggsnr2", tittel = "vedleggTittel2", mimeType = null, fil = null)

		val innsendingsId = soknadService.opprettNySoknad(
			Hjelpemetoder.lagDokumentSoknad(
				brukerId = "12345678901", // Må være samme som i token (pid)
				skjemanr = skjemanr,
				spraak = spraak,
				tittel = tittel,
				tema = tema,
				vedleggsListe = listOf(vedleggDto1, vedleggDto2)
			)
		)

		val fraFyllUt = SkjemaDto(
			subject, skjemanr, nyTittel, tema, nyttSpraak,
			lagDokument(skjemanr, nyTittel, true),
			lagDokument(skjemanr, nyTittel, true),
			listOf(
				lagDokument(
					"T7",
					"tittel1",
					true,
					null,
					true
				),
				lagDokument("N6", "tittel2", true, null, true)
			)
		)
		val requestEntity = HttpEntity(fraFyllUt, Hjelpemetoder.createHeaders(token))

		// Når
		val response = restTemplate.exchange(
			"http://localhost:${serverPort}/fyllUt/v1/soknad/${innsendingsId}", HttpMethod.PUT,
			requestEntity, Unit::class.java
		)
		val oppdatertSoknad = soknadService.hentSoknad(innsendingsId)


		// Så
		assertTrue(response != null)
		assertEquals(200, response.statusCodeValue)
		assertEquals(nyTittel, oppdatertSoknad.tittel)
		assertEquals("en", oppdatertSoknad.spraak, "Språk er oppdatert (blir konvertert til de første 2 bokstavene)")
		assertEquals(4, oppdatertSoknad.vedleggsListe.size, "Hoveddokument, hoveddokumentVariant og to vedlegg")
		assertTrue(
			oppdatertSoknad.vedleggsListe.any { it.vedleggsnr == "T7" && it.tittel == "tittel1" },
			"Skal ha vedlegg T7"
		)
		assertTrue(
			oppdatertSoknad.vedleggsListe.any { it.vedleggsnr == "N6" && it.tittel == "tittel2" },
			"Skal ha vedlegg T6"
		)
		assertEquals(null, response.body)

	}

	private fun lagDokument(
		vedleggsnr: String,
		tittel: String,
		pakrevd: Boolean,
		mimetype: Mimetype? = null,
		erVedlegg: Boolean = false
	): SkjemaDokumentDto {
		return SkjemaDokumentDto(
			vedleggsnr,
			tittel,
			tittel,
			pakrevd,
			"$tittel- Beskrivelse",
			mimetype,
			if (erVedlegg) null else hentFil(mimetype)
		)
	}

	private fun hentFil(mimetype: Mimetype?): ByteArray? =
		when (mimetype) {
			null -> null
			Mimetype.applicationSlashPdf -> Hjelpemetoder.getBytesFromFile("/litenPdf.pdf")
			Mimetype.applicationSlashJson -> Hjelpemetoder.getBytesFromFile("/sanity.json")
			else -> throw RuntimeException("Testing med mimetype = $mimetype er ikke støttet")
		}

}
