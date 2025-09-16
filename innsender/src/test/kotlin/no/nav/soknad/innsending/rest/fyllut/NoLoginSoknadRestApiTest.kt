package no.nav.soknad.innsending.rest.fyllut


import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import io.mockk.clearAllMocks
import io.mockk.every
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenResponse
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.service.FilService
import no.nav.soknad.innsending.service.KodeverkService
import no.nav.soknad.innsending.service.RepositoryUtils
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.service.fillager.Fil
import no.nav.soknad.innsending.service.fillager.FilMetadata
import no.nav.soknad.innsending.service.fillager.FilStatus
import no.nav.soknad.innsending.service.fillager.FillagerInterface
import no.nav.soknad.innsending.service.fillager.FillagerService
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.NoLoginApi
import no.nav.soknad.innsending.utils.TokenGenerator
import no.nav.soknad.innsending.utils.builders.SkjemaDokumentDtoV2TestBuilder
import no.nav.soknad.innsending.utils.builders.SkjemaDtoV2TestBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.*
import java.util.*
import kotlin.test.*


class NoLoginSoknadRestApiTest: ApplicationTest()  {

	@MockkBean
	lateinit var oauth2TokenService: OAuth2AccessTokenService

	@MockkBean
	lateinit var kodeverkService: KodeverkService

	@SpykBean
	lateinit var notificationPublisher: PublisherInterface

	@Autowired
	lateinit var restTemplate: TestRestTemplate

	@Autowired
	lateinit var soknadService: SoknadService

	@Autowired
	lateinit var repo: RepositoryUtils

	@Autowired
	lateinit var filService: FilService

	@Autowired
	private lateinit var fillagerService: FillagerService


	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	val postnummerMap = mapOf(
		"7950" to "ABELVÆR",
		"3812" to "AKKERHAUGEN",
		"5575" to "AKSDAL",
		"7318" to "AGDENES",
	)

	var api: NoLoginApi? = null

	@BeforeEach
	fun setup() {
		clearAllMocks()
		api = NoLoginApi(restTemplate, serverPort!!, mockOAuth2Server)
		every { oauth2TokenService.getAccessToken(any()) } returns OAuth2AccessTokenResponse(access_token = "token")
		every { kodeverkService.getPoststed(any()) } answers { postnummerMap[firstArg()] }
	}

	@Value("\${server.port}")
	var serverPort: Int? = 9064


	@Test
	fun `test opprett og sendinn soknad med vedlegg for uinnlogget bruker`() {
		// Gitt
		val formioId = UUID.randomUUID()
		val innsendingsIdUUID = UUID.randomUUID()

		val uploaded = api!!.uploadFile(
			innsendingsId = innsendingsIdUUID,
			vedleggId = formioId.toString(),
			file = Hjelpemetoder.getBytesFromFile("/litenPdf.pdf"),
			filename = "litenPdf.pdf",
			envQualifier = null)

		val skjemaDokumentDto = SkjemaDokumentDtoV2TestBuilder(
			vedleggsnr = "W2",
			formioId = formioId.toString(),
			tittel = "Skjema for test av innsending",
			label = "Skjema for test av innsending",
			beskrivelse = "Skjema for test av innsending",
			opplastingsStatus = OpplastingsStatusDto.LastetOpp,
			mimetype = Mimetype.applicationSlashPdf,
			filIdListe = listOf(uploaded.body.filId.toString())
		).build()

		val skjemaDto = SkjemaDtoV2TestBuilder()
			.medBrukerId("12345678901")
			.medInnsendingsId(innsendingsIdUUID.toString())
			.medVedlegg(skjemaDokumentDto)
			.medMellomlagringDager(1)
			.build()

		// Når
		val innsendtSoknadResponse = api!!.createAndSendInSoknad(
			dokumentDto = skjemaDto,
			envQualifier = EnvQualifier.preprodAltAnsatt
			)
			.assertSuccess()

		// Så
		assertTrue(innsendtSoknadResponse.statusCode == HttpStatus.OK )
		val kvitteringsDto = innsendtSoknadResponse.body
		assertEquals(0, kvitteringsDto.skalSendesAvAndre!!.size)
		assertTrue(kvitteringsDto.hoveddokumentRef == null)
	}


}
