package no.nav.soknad.innsending.rest

import com.nimbusds.jose.JOSEObjectType
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.security.token.support.spring.test.MockLoginController
import no.nav.soknad.innsending.InnsendingApiApplication
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.utils.createHeaders
import no.nav.soknad.innsending.utils.getBytesFromFile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Suppress("DEPRECATION")
@ActiveProfiles("test")
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
	properties = ["spring.main.allow-bean-definition-overriding=true"],
	classes = [InnsendingApiApplication::class])
@ExtendWith(SpringExtension::class)
@AutoConfigureWireMock
@EnableMockOAuth2Server(port = 9898)
class SkjemaRestApiTest {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var restTemplate: TestRestTemplate


	@Value("\${server.port}")
	var serverPort: Int? = 9064

	private val tokenx = "tokenx"
	private val subject = "12345678901"
	private val audience = "aud-localhost"
	private val expiry = 2*3600

	@Test
	internal fun testOpprettSoknadPaFyllUtApi() {
		val token: String = mockOAuth2Server.issueToken(
			tokenx,
			MockLoginController::class.java.simpleName,
			DefaultOAuth2TokenCallback(
				tokenx,
				subject,
				JOSEObjectType.JWT.type,
				listOf(audience),
				mapOf("acr" to "Level4"),
				expiry.toLong()
			)
		).serialize()

		val skjemanr = "NAV 14-05.07"
		val tittel = "Søknad om engangsstønad ved fødsel"
		val tema = "FOR"
		val sprak = "no_nb"

		val fraFyllUt = SkjemaDto(subject, skjemanr, tittel, tema, sprak,
			lagDokument(skjemanr, tittel, true, Mimetype.applicationSlashPdf),
			lagDokument(skjemanr, tittel, true, Mimetype.applicationSlashJson),
			listOf(
				lagDokument("T7", "Inntektsopplysninger for selvstendig næringsdrivende og frilansere som skal ha foreldrepenger eller svangerskapspenger", true, null, true),
				lagDokument("N6", "Dokumentasjon av veiforhold", true, null, true)
			)
		)
		val requestEntity =	HttpEntity(fraFyllUt, createHeaders(token))

		val response = restTemplate.exchange("http://localhost:${serverPort}/fyllUt/v1/leggTilVedlegg", HttpMethod.POST,
			requestEntity, Unit::class.java)

		assertTrue(response != null)

		assertEquals(302, response.statusCodeValue)
		assertTrue(response.headers["Location"] != null)

		testHentSoknadOgSendInn(response, token)

	}

	private fun testHentSoknadOgSendInn(
		response: ResponseEntity<Unit>,
		token: String
	) {
		val innsendingsId = response.headers["Location"]?.first()?.substringAfterLast("/")
		assertNotNull(innsendingsId)

		val getRequestEntity = HttpEntity<Unit>(createHeaders(token))

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
		val patchRequestT7 = HttpEntity(patchVedleggT7, createHeaders(token))
		val patchResponseT7 = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${innsendingsId}/vedlegg/${vedleggT7.id}", HttpMethod.PATCH,
			patchRequestT7, VedleggDto::class.java
		)

		assertTrue(patchResponseT7.body != null)
		assertEquals(OpplastingsStatusDto.sendesAvAndre, patchResponseT7.body!!.opplastingsStatus)

		val vedleggN6 = getSoknadDto.vedleggsListe.first { it.vedleggsnr == "N6" }
		val patchVedleggN6 = PatchVedleggDto(null, OpplastingsStatusDto.sendesAvAndre)
		val patchRequestN6 = HttpEntity(patchVedleggN6, createHeaders(token))
		val patchResponseN6 = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${innsendingsId}/vedlegg/${vedleggN6.id}", HttpMethod.PATCH,
			patchRequestN6, VedleggDto::class.java
		)

		assertTrue(patchResponseN6.body != null)
		assertEquals(OpplastingsStatusDto.sendesAvAndre, patchResponseN6.body!!.opplastingsStatus)

		///frontend/v1/sendInn/{innsendingsId}
		val sendInnRespons = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/sendInn/${innsendingsId}", HttpMethod.POST,
			HttpEntity<Unit>(createHeaders(token)), KvitteringsDto::class.java
		)

		assertTrue(sendInnRespons.statusCode == HttpStatus.OK && sendInnRespons.body != null)
		val kvitteringsDto = sendInnRespons.body
		assertEquals(2, kvitteringsDto!!.skalSendesAvAndre!!.size)
	}

	private fun lagDokument(vedleggsnr: String, tittel: String, pakrevd: Boolean, mimetype: Mimetype? = null, erVedlegg: Boolean = false): SkjemaDokumentDto {
		return SkjemaDokumentDto(vedleggsnr, tittel, tittel, pakrevd, "$tittel- Beskrivelse", mimetype, if (erVedlegg) null else hentFil(mimetype))
	}

	private fun hentFil(mimetype: Mimetype?): ByteArray? =
		when(mimetype) {
			null ->  null
			Mimetype.applicationSlashPdf ->  getBytesFromFile("/litenPdf.pdf")
			Mimetype.applicationSlashJson ->  getBytesFromFile("/sanity.json")
			else -> throw RuntimeException("Testing med mimetype = $mimetype er ikke støttet")
		}

}
