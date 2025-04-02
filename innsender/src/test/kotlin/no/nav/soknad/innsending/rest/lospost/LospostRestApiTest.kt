package no.nav.soknad.innsending.rest.lospost

import com.ninjasquad.springmockk.SpykBean
import io.mockk.clearAllMocks
import io.mockk.slot
import io.mockk.verify
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.model.EnvQualifier
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.OpprettLospost
import no.nav.soknad.innsending.model.PostVedleggDto
import no.nav.soknad.innsending.utils.Api
import no.nav.soknad.innsending.utils.Hjelpemetoder
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

	@SpykBean
	lateinit var notificationPublisher: PublisherInterface

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

	@Test
	fun `Should create lospost innsending`() {
		val request = OpprettLospost(
			soknadTittel = "BIL - Førerkort",
			tema = "BIL",
			dokumentTittel = "Førerkort",
			sprak = "nb"
		)
		val response = api.createLospost(request)
			.assertSuccess()

		val body = response.body
		assertEquals(request.soknadTittel, body.tittel)
		assertEquals(request.tema, body.tema)
		assertEquals(request.sprak, body.spraak)
		assertEquals(1, body.vedleggsListe?.size)

		val vedlegg = body.vedleggsListe?.first()!!
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
		val createResponse = api.createLospost(request)
			.assertSuccess()

		val body = createResponse.body
		assertEquals(1, body.vedleggsListe?.size)

		val vedlegg = body.vedleggsListe?.first()!!
		assertEquals("N6", vedlegg.vedleggsnr)
		assertEquals(request.dokumentTittel, vedlegg.tittel)
		assertEquals(OpplastingsStatusDto.IkkeValgt, vedlegg.opplastingsStatus)
		val file35MB = loadFile35MB()
		api.uploadFile(body.innsendingsId!!, vedlegg.id!!, file35MB)
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
		val createResponse = api.createLospost(request)
			.assertSuccess()

		val body = createResponse.body
		assertEquals(1, body.vedleggsListe?.size)

		val vedlegg = body.vedleggsListe?.first()!!
		assertEquals("N6", vedlegg.vedleggsnr)
		assertEquals(request.dokumentTittel, vedlegg.tittel)
		assertEquals(OpplastingsStatusDto.IkkeValgt, vedlegg.opplastingsStatus)
		val file10MB = loadFile10MB()
		api.uploadFile(body.innsendingsId!!, vedlegg.id!!, file10MB)
			.assertSuccess()
		api.uploadFile(body.innsendingsId!!, vedlegg.id!!, file10MB)
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
		val createResponse = api.createLospost(request)
			.assertSuccess()

		val body = createResponse.body
		assertEquals(1, body.vedleggsListe?.size)
		val innsendingsId = body.innsendingsId!!

		val vedleggMain = body.vedleggsListe?.first()!!
		assertEquals("N6", vedleggMain.vedleggsnr)
		assertEquals(request.dokumentTittel, vedleggMain.tittel)
		assertEquals(OpplastingsStatusDto.IkkeValgt, vedleggMain.opplastingsStatus)
		val file14MB = loadFile14MB()
		api.uploadFile(innsendingsId, vedleggMain.id!!, file14MB)
			.assertSuccess()

		val vedlegg2 = api.addVedlegg(innsendingsId, PostVedleggDto("Mer informasjon"))
			.assertSuccess()
			.body
		api.uploadFile(innsendingsId, vedlegg2.id!!, file14MB)
			.assertSuccess()

		val vedlegg3 = api.addVedlegg(innsendingsId, PostVedleggDto("Litt mer informasjon"))
			.assertSuccess()
			.body
		api.uploadFile(innsendingsId, vedlegg3.id!!, file14MB)
			.assertSuccess()

		val vedlegg4 = api.addVedlegg(innsendingsId, PostVedleggDto("Enda mer informasjon"))
			.assertSuccess()
			.body
		api.uploadFile(innsendingsId, vedlegg4.id!!, file14MB)
			.assertClientError()
			.assertErrorCode(ErrorCode.FILE_SIZE_SUM_TOO_LARGE)
	}

	@Test
	fun `Should resolve urls based on env qualifier ansatt`() {
		val request = OpprettLospost(
			soknadTittel = "PEN - Arbeidskontrakt",
			tema = "PEN",
			dokumentTittel = "Arbeidskontrakt",
			sprak = "nb"
		)

		val response = api.createLospost(request, EnvQualifier.preprodAnsatt)
			.assertSuccess()
		assertContains(response.headers?.location.toString(), "ansatt.dev.nav.no/sendinn")

		val parameterSlot = slot<AddNotification>()
		verify(exactly = 1) { notificationPublisher.opprettBrukernotifikasjon(capture(parameterSlot)) }
		val notification = parameterSlot.captured
		assertTrue(
			notification.brukernotifikasjonInfo.lenke.contains("ansatt.dev.nav.no/sendinn"),
			"Link not expected: ${notification.brukernotifikasjonInfo.lenke}"
		)
	}

	@Test
	fun `Should resolve urls based on env qualifier intern`() {
		val request = OpprettLospost(
			soknadTittel = "PEN - Arbeidskontrakt",
			tema = "PEN",
			dokumentTittel = "Arbeidskontrakt",
			sprak = "nb"
		)

		val response = api.createLospost(request, EnvQualifier.preprodIntern)
			.assertSuccess()
		assertContains(response.headers?.location.toString(), "intern.dev.nav.no/sendinn")

		val parameter = slot<AddNotification>()
		verify(exactly = 1) { notificationPublisher.opprettBrukernotifikasjon(capture(parameter)) }
		val notification = parameter.captured
		assertTrue(
			notification.brukernotifikasjonInfo.lenke.contains("intern.dev.nav.no/sendinn"),
			"Link not expected: ${notification.brukernotifikasjonInfo.lenke}"
		)
	}

	@Test
	fun `Should create notification with correct information`() {
		val request = OpprettLospost(
			soknadTittel = "PEN - Arbeidskontrakt",
			tema = "PEN",
			dokumentTittel = "Arbeidskontrakt",
			sprak = "nb"
		)

		val lospostDto = api.createLospost(request)
			.assertSuccess()
			.body
		assertNotNull(lospostDto.innsendingsId)

		val parameterSlot = slot<AddNotification>()
		verify(exactly = 1) { notificationPublisher.opprettBrukernotifikasjon(capture(parameterSlot)) }
		val notification = parameterSlot.captured

		assertEquals(lospostDto.innsendingsId, notification.soknadRef.innsendingId)
		assertEquals(lospostDto.innsendingsId, notification.soknadRef.groupId)
		assertEquals(lospostDto.brukerId, notification.soknadRef.personId)
		assertEquals(false, notification.soknadRef.erSystemGenerert)
		assertEquals(false, notification.soknadRef.erEttersendelse)
		assertEquals(lospostDto.tittel, notification.brukernotifikasjonInfo.notifikasjonsTittel)
		assertEquals(1, notification.brukernotifikasjonInfo.antallAktiveDager)
		assertNotNull(notification.brukernotifikasjonInfo.lenke)
		assertEquals(0, notification.brukernotifikasjonInfo.eksternVarsling.size)
		assertNull(notification.brukernotifikasjonInfo.utsettSendingTil)
	}

	fun loadFile10MB() = Hjelpemetoder.getBytesFromFile("/mellomstor-fra-jpg.pdf")

	fun loadFile35MB() = Hjelpemetoder.getBytesFromFile("/mellomstorJpg.jpg")

	fun loadFile14MB() = Hjelpemetoder.getBytesFromFile("/mellomstor.pdf")

}
