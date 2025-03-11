package no.nav.soknad.innsending.rest.lospost

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.model.EnvQualifier
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.OpprettLospost
import no.nav.soknad.innsending.model.PostVedleggDto
import no.nav.soknad.innsending.utils.Api
import no.nav.soknad.innsending.utils.Hjelpemetoder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import kotlin.test.assertContains
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
	fun `Should create lospost innsending`() {
		val request = OpprettLospost(
			soknadTittel = "BIL - Førerkort",
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

	@Test
	fun `Should reject too large file`() {
		val request = OpprettLospost(
			soknadTittel = "BIL - Førerkort",
			tema = "BIL",
			dokumentTittel = "Førerkort",
			sprak = "nb"
		)
		val createResponse = api?.createLospost(request)
		assertNotNull(createResponse?.body)

		val body = createResponse.body!!
		assertEquals(1, body.vedleggsListe?.size)

		val vedlegg = createResponse.body?.vedleggsListe?.first()!!
		assertEquals("N6", vedlegg.vedleggsnr)
		assertEquals(request.dokumentTittel, vedlegg.tittel)
		assertEquals(OpplastingsStatusDto.IkkeValgt, vedlegg.opplastingsStatus)
		val file35MB = loadFile35MB()
		api!!.uploadFile(body.innsendingsId!!, vedlegg.id!!, file35MB)
			.assertClientError()
			.assertErrorCode(ErrorCode.VEDLEGG_FILE_SIZE_SUM_TOO_LARGE)
	}

	@Test
	fun `Should reject second file upload when total file size is to large`() {
		val request = OpprettLospost(
			soknadTittel = "BIL - Førerkort",
			tema = "BIL",
			dokumentTittel = "Førerkort",
			sprak = "nb"
		)
		val createResponse = api?.createLospost(request)
		assertNotNull(createResponse?.body)

		val body = createResponse.body!!
		assertEquals(1, body.vedleggsListe?.size)

		val vedlegg = createResponse.body?.vedleggsListe?.first()!!
		assertEquals("N6", vedlegg.vedleggsnr)
		assertEquals(request.dokumentTittel, vedlegg.tittel)
		assertEquals(OpplastingsStatusDto.IkkeValgt, vedlegg.opplastingsStatus)
		val file10MB = loadFile10MB()
		api!!.uploadFile(body.innsendingsId!!, vedlegg.id!!, file10MB)
			.assertSuccess()
		api!!.uploadFile(body.innsendingsId!!, vedlegg.id!!, file10MB)
			.assertClientError()
			.assertErrorCode(ErrorCode.VEDLEGG_FILE_SIZE_SUM_TOO_LARGE)
	}

	@Test
	fun `Should reject upload since total size for application exceeds max limit`() {
		val request = OpprettLospost(
			soknadTittel = "BIL - Førerkort",
			tema = "BIL",
			dokumentTittel = "Førerkort",
			sprak = "nb"
		)
		val createResponse = api?.createLospost(request)
		assertNotNull(createResponse?.body)

		val body = createResponse.body!!
		assertEquals(1, body.vedleggsListe?.size)
		val innsendingsId = body.innsendingsId!!

		val vedleggMain = createResponse.body?.vedleggsListe?.first()!!
		assertEquals("N6", vedleggMain.vedleggsnr)
		assertEquals(request.dokumentTittel, vedleggMain.tittel)
		assertEquals(OpplastingsStatusDto.IkkeValgt, vedleggMain.opplastingsStatus)
		val file14MB = loadFile14MB()
		api!!.uploadFile(innsendingsId, vedleggMain.id!!, file14MB)
			.assertSuccess()

		val vedlegg2 = api!!.addVedlegg(innsendingsId, PostVedleggDto("Mer informasjon"))
			.assertSuccess()
			.body
		api!!.uploadFile(innsendingsId, vedlegg2.id!!, file14MB)
			.assertSuccess()

		val vedlegg3 = api!!.addVedlegg(innsendingsId, PostVedleggDto("Litt mer informasjon"))
			.assertSuccess()
			.body
		api!!.uploadFile(innsendingsId, vedlegg3.id!!, file14MB)
			.assertSuccess()

		val vedlegg4 = api!!.addVedlegg(innsendingsId, PostVedleggDto("Enda mer informasjon"))
			.assertSuccess()
			.body
		api!!.uploadFile(innsendingsId, vedlegg4.id!!, file14MB)
			.assertClientError()
			.assertErrorCode(ErrorCode.FILE_SIZE_SUM_TOO_LARGE)
	}

	@Test
	fun `Should return location based on env qualifier`() {
		val request = OpprettLospost(
			soknadTittel = "PEN - Arbeidskontrakt",
			tema = "PEN",
			dokumentTittel = "Arbeidskontrakt",
			sprak = "nb"
		)

		val responseAnsatt = api?.createLospost(request, EnvQualifier.preprodAnsatt)
		assertContains(responseAnsatt?.headers?.location.toString(), "ansatt.dev.nav.no")

		val responseIntern = api?.createLospost(request, EnvQualifier.preprodIntern)
		assertContains(responseIntern?.headers?.location.toString(), "intern.dev.nav.no")
	}

	fun loadFile10MB() = Hjelpemetoder.getBytesFromFile("/mellomstor-fra-jpg.pdf")

	fun loadFile35MB() = Hjelpemetoder.getBytesFromFile("/mellomstorJpg.jpg")

	fun loadFile14MB() = Hjelpemetoder.getBytesFromFile("/mellomstor.pdf")

}
