package no.nav.soknad.innsending.rest.lospost

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.OpprettLospost
import no.nav.soknad.innsending.utils.Api
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LospostRestApiTest : ApplicationTest() {

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
	fun `Should create løspost innsending`() {
		val request = OpprettLospost(
			soknadTittel = "Send dokumenter til NAV",
			tema = "BIL",
			dokumentTittel = "Førerkort",
			sprak = "nb"
		)
		val response = api?.createLospost(request)
		assertNotNull(response?.body)

		val body = response.body!!
		assertEquals(request.soknadTittel, body.tittel)
		assertEquals(request.tema, body.tema)
		assertEquals(request.sprak, body.spraak)
		assertEquals(1, body.vedleggsListe?.size)

		val vedlegg = response.body?.vedleggsListe?.first()!!
		assertEquals("N6", vedlegg.vedleggsnr)
		assertEquals(request.dokumentTittel, vedlegg.tittel)
		assertEquals(OpplastingsStatusDto.IkkeValgt, vedlegg.opplastingsStatus)
	}

}
