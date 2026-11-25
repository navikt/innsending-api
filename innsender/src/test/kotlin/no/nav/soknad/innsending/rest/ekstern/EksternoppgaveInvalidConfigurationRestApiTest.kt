package no.nav.soknad.innsending.rest.ekstern

import com.ninjasquad.springmockk.MockkSpyBean
import io.mockk.verify
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.model.EksternEttersendingsOppgave
import no.nav.soknad.innsending.model.InnsendtVedleggDto
import no.nav.soknad.innsending.util.testpersonid
import no.nav.soknad.innsending.utils.ApiWebClient
import no.nav.soknad.innsending.utils.TokenGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@TestPropertySource(properties = ["task-authorization.pre-authorized-apps=not-json"])
class EksternoppgaveInvalidConfigurationRestApiTest : ApplicationTest() {
	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@MockkSpyBean
	lateinit var publisher: PublisherInterface

	@LocalServerPort
	var serverPort: Int = 0

	@Test
	fun `should deny an otherwise authorized task caller when preauthorization configuration is malformed`() {
		val api = ApiWebClient(webTestClient, serverPort, mockOAuth2Server)
		val task = EksternEttersendingsOppgave(
			brukerId = testpersonid,
			skjemanr = "NAV 55-00.60",
			sprak = "nb_NO",
			tema = "BID",
			tittel = "Test task",
			koblesTilEksisterendeSoknad = false,
			vedleggsListe = listOf(InnsendtVedleggDto(vedleggsnr = "W1", tittel = "Test attachment")),
		)
		val response = api.createEttersendingsOppgaveFail(task, token = TokenGenerator(mockOAuth2Server).taskApplicationToken())
		assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
		assertEquals("errorCode.forbidden", assertNotNull(response.body).errorCode)
		verify(exactly = 0) { publisher.opprettBrukernotifikasjon(any()) }
	}
}
