package no.nav.soknad.innsending.rest.fyllut

import com.ninjasquad.springmockk.SpykBean
import io.mockk.clearAllMocks
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.model.Mimetype
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.service.config.ConfigDefinition
import no.nav.soknad.innsending.service.config.ConfigService
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.utils.Api
import no.nav.soknad.innsending.utils.builders.SkjemaDokumentDtoV2TestBuilder
import no.nav.soknad.innsending.utils.builders.SkjemaDtoV2TestBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

class NoLoginSoknadRestApiTest : ApplicationTest() {
	@Autowired
	lateinit var configService: ConfigService

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var restTemplate: TestRestTemplate

	@SpykBean
	lateinit var metrics: InnsenderMetrics

	@Value("\${server.port}")
	var serverPort: Int? = 9064

	var testApi: Api? = null
	val api: Api
		get() = testApi!!

	@BeforeEach
	fun setup() {
		testApi = Api(restTemplate, serverPort!!, mockOAuth2Server)
		clearAllMocks()
		api.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "on")
			.assertSuccess()
	}

	@Test
	fun `test opprett og sendinn soknad med vedlegg for uinnlogget bruker`() {
		val file1 = api.uploadNologinFile(vedleggId = "abcdef")
			.assertSuccess()
			.body
		val innsendingId = file1.innsendingId.toString()

		val vedlegg1 = SkjemaDokumentDtoV2TestBuilder(
			opplastingsStatus = OpplastingsStatusDto.LastetOpp,
			mimetype = Mimetype.applicationSlashPdf,
			filIdListe = listOf(file1.filId.toString())
		).build()

		val skjemaDto = SkjemaDtoV2TestBuilder()
			.medBrukerId("12345678901")
			.medInnsendingsId(innsendingId)
			.medVedlegg(listOf(vedlegg1))
			.build()

		val kvitteringsDto = api.sendInnNologinSoknad(skjemaDto)
			.assertSuccess()
			.body

		assertEquals(0, kvitteringsDto.skalSendesAvAndre!!.size)
		assertEquals(kvitteringsDto.hoveddokumentRef, null)
	}

	@Test
	fun `skal avvise innsending dersom soknad allerede er sendt inn`() {
		val file1 = api.uploadNologinFile(vedleggId = "abcdef")
			.assertSuccess()
			.body
		val innsendingId = file1.innsendingId.toString()

		val vedlegg1 = SkjemaDokumentDtoV2TestBuilder(
			opplastingsStatus = OpplastingsStatusDto.LastetOpp,
			mimetype = Mimetype.applicationSlashPdf,
			filIdListe = listOf(file1.filId.toString())
		).build()

		val skjemaDto = SkjemaDtoV2TestBuilder()
			.medBrukerId("12345678901")
			.medInnsendingsId(innsendingId)
			.medVedlegg(listOf(vedlegg1))
			.build()

		api.sendInnNologinSoknad(skjemaDto)
			.assertSuccess()

		api.sendInnNologinSoknad(skjemaDto)
			.assertClientError()
			.errorBody.let {
				assertEquals("Søknad med innsendingsId ${skjemaDto.innsendingsId} finnes allerede", it.message)
			}
	}

	@Test
	fun `skal avvise innsending dersom nologin main switch er av`() {
		val file1 = api.uploadNologinFile(vedleggId = "abcdef")
			.assertSuccess()
			.body
		val innsendingId = file1.innsendingId.toString()

		val vedlegg1 = SkjemaDokumentDtoV2TestBuilder(
			opplastingsStatus = OpplastingsStatusDto.LastetOpp,
			mimetype = Mimetype.applicationSlashPdf,
			filIdListe = listOf(file1.filId.toString())
		).build()

		val skjemaDto = SkjemaDtoV2TestBuilder()
			.medBrukerId("12345678901")
			.medInnsendingsId(innsendingId)
			.medVedlegg(listOf(vedlegg1))
			.build()

		configService.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "off", "test")
		api.sendInnNologinSoknad(skjemaDto)
			.assertHttpStatus(HttpStatus.SERVICE_UNAVAILABLE)
	}


}
