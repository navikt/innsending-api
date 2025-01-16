package no.nav.soknad.innsending.rest.ekstern

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.InnsendingApiApplication
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.EksternEttersendingsOppgave
import no.nav.soknad.innsending.model.InnsendtVedleggDto
import no.nav.soknad.innsending.model.SoknadType
import no.nav.soknad.innsending.service.VedleggService
import no.nav.soknad.innsending.utils.Api
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.junit.jupiter.api.Test
import org.springframework.http.*


@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
	properties = ["spring.main.allow-bean-definition-overriding=true"],
	classes = [InnsendingApiApplication::class]
)
@ExtendWith(SpringExtension::class)
class InternInitiertOppgaverTest: ApplicationTest() {

	@Autowired
	private lateinit var vedleggService: VedleggService

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
	fun `happy case create task`() {
		val brukerId = "12345678901"
		val vedlegg = listOf("W1", "W2")
		val skjemanr = "NAV 55-00.60"
		val soknadDto = opprettSoknad(brukerId, vedlegg, skjemanr)

		assertEquals(brukerId, soknadDto.brukerId)
		assertEquals(skjemanr, soknadDto.skjemanr)
		assertEquals(true, soknadDto.erNavInitiert)
	}


	@Test
	fun `happy case delete application`() {
		val brukerId = "12345678901"
		val vedlegg = listOf("W1", "W2")
		val skjemanr = "NAV 55-00.60"
		val soknadDto = opprettSoknad(brukerId, vedlegg, skjemanr)

		val response = api?.eksternOppgaveSlett(soknadDto.innsendingsId!! )

		assertNotNull(response)
		assertEquals(HttpStatus.OK, response?.statusCode)
	}

	@Test
	fun `Not found delete application`() {
		val brukerId = "12345678901"
		val vedlegg = listOf("W1", "W2")
		val skjemanr = "NAV 55-00.60"
		val soknadDto = opprettSoknad(brukerId, vedlegg, skjemanr)

		val response = api?.eksternOppgaveSlettFail("12345" )

		assertNotNull(response)
		assertEquals(HttpStatus.NOT_FOUND, response?.statusCode)
	}


	@Test
	fun `happy case hent soknader`() {
		val brukerId = "12345678901"
		val brukerId2 = "12121212121"
		val vedlegg = listOf("W1", "W2")
		val skjemanr = "NAV 55-00.60"
		val skjemanr2 = "NAV 04-02.01"

		// Given
		val soknadDto = opprettSoknad(brukerId, vedlegg, skjemanr)
		val soknadDto2 = opprettSoknad(brukerId, vedlegg, skjemanr2)
		val soknadDto3 = opprettSoknad(brukerId2, vedlegg, skjemanr)

		// When
		val response = api?.oppgaveHentSoknaderForSkjemanr(skjemanr, brukerId, listOf(SoknadType.ettersendelse), "nav-call-id" )

		// Then
		assertNotNull(response)
		assertEquals(HttpStatus.OK, response?.statusCode)

		val list = response?.body
		assertEquals(1, list?.size)
		assertEquals(brukerId, list?.get(0)?.brukerId)
	}

	private fun opprettSoknad(brukerId: String, vedlegg: List<String>, skjemanr: String): DokumentSoknadDto {
		val vedleggsListe = mutableListOf<InnsendtVedleggDto>()
		vedlegg.forEach { vedleggsListe.add(InnsendtVedleggDto(vedleggsnr = it, tittel = "Tittel"+it, url = null)) }
		val oppgave = EksternEttersendingsOppgave(
			brukerId = brukerId,
			skjemanr = skjemanr,
			sprak = "nb_NO",
			tema = "BID",
			tittel = "Avtale om barnebidrag",
			brukernotifikasjonstype = null,
			koblesTilEksisterendeSoknad = false,
			vedleggsListe = vedleggsListe
		)

		val response = api?.createEttersendingsOppgave(oppgave )

		assertNotNull(response)
		assertEquals(HttpStatus.CREATED, response?.statusCode)
		return response?.body!!

	}


}
