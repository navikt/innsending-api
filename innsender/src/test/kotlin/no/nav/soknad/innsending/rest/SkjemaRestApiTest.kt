package no.nav.soknad.innsending.rest

import com.nimbusds.jose.JOSEObjectType
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.security.token.support.spring.test.MockLoginController
import no.nav.soknad.innsending.InnsendingApiApplication
import no.nav.soknad.innsending.model.Mimetype
import no.nav.soknad.innsending.model.SkjemaDokumentDto
import no.nav.soknad.innsending.model.SkjemaDto
import no.nav.soknad.innsending.utils.createHeaders
import no.nav.soknad.innsending.utils.getBytesFromFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import kotlin.test.assertEquals
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
	internal fun testHentJournalposter() {
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
			listOf(lagDokument("T7", "Inntektsopplysninger for selvstendig næringsdrivende og frilansere som skal ha foreldrepenger eller svangerskapspenger", true, Mimetype.applicationSlashPdf)),
		)
		val requestEntity =	HttpEntity(fraFyllUt, createHeaders(token))

		val response = restTemplate.exchange("http://localhost:${serverPort}/fyllUt/v1/leggTilVedlegg", HttpMethod.POST,
			requestEntity, Unit::class.java)

		assertTrue(response != null)

		assertEquals(302, response.statusCodeValue)
		assertTrue(response.headers["Location"] != null)

	}

	private fun lagDokument(vedleggsnr: String, tittel: String, pakrevd: Boolean, mimetype: Mimetype? = null): SkjemaDokumentDto {
		return SkjemaDokumentDto(vedleggsnr, tittel, tittel, pakrevd, "$tittel- Beskrivelse", mimetype, hentFil(mimetype))
	}

	private fun hentFil(mimetype: Mimetype?): ByteArray? =
		when(mimetype) {
			null ->  null
			Mimetype.applicationSlashPdf ->  getBytesFromFile("/litenPdf.pdf")
			Mimetype.applicationSlashJson ->  getBytesFromFile("/sanity.json")
			else -> throw RuntimeException("Testing med mimetype = $mimetype er ikke støttet")
		}

}
