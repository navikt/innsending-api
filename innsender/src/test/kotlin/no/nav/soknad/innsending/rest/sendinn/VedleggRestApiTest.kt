package no.nav.soknad.innsending.rest.sendinn

import io.mockk.clearAllMocks
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.utils.Api
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.TokenGenerator
import no.nav.soknad.innsending.utils.builders.SkjemaDokumentDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.SkjemaDtoTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod

class VedleggRestApiTest : ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var restTemplate: TestRestTemplate

	@Value("\${server.port}")
	var serverPort: Int? = 9064

	var testApi: Api? = null
	val api: Api
		get() = testApi!!

	@BeforeEach
	fun setup() {
		testApi = Api(restTemplate, serverPort!!, mockOAuth2Server)
		clearAllMocks()
	}

	private val defaultSkjemanr = "NAV 55-00.60"

	@Test
	fun oppdrettVedleggTest() {
		val skjemanr = defaultSkjemanr
		val spraak = "nb_NO"
		val vedlegg = emptyList<String>()

		val token: String = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val opprettetSoknadDto = opprettEnSoknad(skjemanr, spraak, vedlegg)
		assertTrue(opprettetSoknadDto.vedleggsListe.isNotEmpty())

		val postVedleggDto = PostVedleggDto("Nytt vedlegg")
		val postVedleggRequestEntity = HttpEntity(postVedleggDto, Hjelpemetoder.createHeaders(token))
		val postVedleggResponse = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${opprettetSoknadDto.innsendingsId}/vedlegg", HttpMethod.POST,
			postVedleggRequestEntity, VedleggDto::class.java
		)

		assertTrue(postVedleggResponse.body != null)
		val nyttVedleggDto = postVedleggResponse.body
		assertTrue(nyttVedleggDto != null)
		assertEquals("Nytt vedlegg", nyttVedleggDto!!.tittel)
	}

	@Test
	fun oppdaterVedleggTest() {
		val skjemanr = defaultSkjemanr
		val spraak = "nb_NO"
		val vedlegg = listOf("N6")
		val token: String = TokenGenerator(mockOAuth2Server).lagTokenXToken()
		val opprettetSoknadDto = opprettEnSoknad(skjemanr, spraak, vedlegg)

		val vedleggDto = opprettetSoknadDto.vedleggsListe.first { !it.erHoveddokument }
		val patchVedleggDto = PatchVedleggDto("Endret tittel", OpplastingsStatusDto.SendesAvAndre, opplastingsValgKommentarLedetekst = "Hvem sender inn dokumentasjonen", opplastingsValgKommentar = "Sendes av min fastlege")
		val patchRequestEntity = HttpEntity(patchVedleggDto, Hjelpemetoder.createHeaders(token))
		val patchResponse = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${opprettetSoknadDto.innsendingsId}/vedlegg/${vedleggDto.id}",
			HttpMethod.PATCH,
			patchRequestEntity,
			VedleggDto::class.java
		)

		assertTrue(patchResponse.body != null)
		val patchedVedleggDto = patchResponse.body
		assertEquals(vedleggDto.id, patchedVedleggDto!!.id)
		assertEquals("Endret tittel", patchedVedleggDto.tittel)
		assertEquals(OpplastingsStatusDto.SendesAvAndre, patchedVedleggDto.opplastingsStatus)
		assertEquals("Hvem sender inn dokumentasjonen", patchedVedleggDto.opplastingsValgKommentarLedetekst)
		assertEquals("Sendes av min fastlege", patchedVedleggDto.opplastingsValgKommentar)
	}

	private fun opprettEnSoknad(
		skjemanr: String,
		spraak: String,
		vedlegg: List<String>
	): DokumentSoknadDto {
		val requestBody = SkjemaDtoTestBuilder(
			skjemanr = skjemanr,
			spraak = spraak,
			vedleggsListe = vedlegg.map { SkjemaDokumentDtoTestBuilder(vedleggsnr = it).build() }
		).build()
		val dokumentSoknadDto = api.createSoknad(requestBody)
			.assertSuccess()
			.body
		return api.getSoknadSendinn(dokumentSoknadDto.innsendingsId!!).assertSuccess().body
	}


}
