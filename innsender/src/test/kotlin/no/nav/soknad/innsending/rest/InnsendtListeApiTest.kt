package no.nav.soknad.innsending.rest

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.nimbusds.jose.JOSEObjectType
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.security.token.support.spring.test.MockLoginController
import no.nav.soknad.innsending.InnsendingApiApplication
import no.nav.soknad.innsending.model.AktivSakDto
import no.nav.soknad.innsending.safselvbetjening.generated.HentDokumentOversikt
import no.nav.soknad.innsending.safselvbetjening.generated.enums.Datotype
import no.nav.soknad.innsending.safselvbetjening.generated.enums.Journalposttype
import no.nav.soknad.innsending.safselvbetjening.generated.enums.Journalstatus
import no.nav.soknad.innsending.safselvbetjening.generated.enums.Kanal
import no.nav.soknad.innsending.safselvbetjening.generated.hentdokumentoversikt.*
import no.nav.soknad.innsending.utils.createHeaders
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.junit.jupiter.api.Assertions.*
import java.util.List
import java.util.Map


@Suppress("DEPRECATION")
@ActiveProfiles("test")
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
	properties = ["spring.main.allow-bean-definition-overriding=true"],
	classes = [InnsendingApiApplication::class])
@ExtendWith(SpringExtension::class)
@AutoConfigureWireMock
@EnableMockOAuth2Server(port = 9898)
internal class InnsendtListeApiTest {

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
				List.of(audience),
				Map.of("acr", "Level4"),
				if (expiry != null) expiry.toLong() else 3600
			)
		).serialize()

		val requestEntity =	HttpEntity<Unit>(createHeaders(token))

	  val response = restTemplate.exchange("http://localhost:${serverPort}/innsendte/v1/hentAktiveSaker", HttpMethod.GET,
			requestEntity, object : ParameterizedTypeReference<List<AktivSakDto>>() {})

		assertTrue(response.body != null && response.body!!.isNotEmpty())
		assertEquals(2, response.body!!.get(0).innsendtVedleggDtos.size)

	}

	@Test
	internal fun testResult() {
		val hentDokumentOversikt = HentDokumentOversikt(HentDokumentOversikt.Variables("12345678901"))
		val dokumentoversikt = Dokumentoversikt(listOf(lagJournalpost()))

		val gson = Gson()
		val gsonPretty = GsonBuilder().setPrettyPrinting().create()
		val json: String = gson.toJson(dokumentoversikt)
		println(json)
		val jsonTutPretty: String = gsonPretty.toJson(json)
		println(jsonTutPretty)

	}

	private fun lagJournalpost(): Journalpost {
		return Journalpost("123", "Tittel", "12345678", Journalstatus.JOURNALFOERT,
			Journalposttype.I, "AAP", Kanal.NAV_NO, listOf(RelevantDato("2022-05-24T11:02:30", Datotype.DATO_OPPRETTET)),
			AvsenderMottaker("12345678901"),
			listOf(DokumentInfo("NAV 08-09.06", "NAV 08-09.06"), DokumentInfo("N6", "Et vedleggEgenerklæring og sykmelding")))
	}

}