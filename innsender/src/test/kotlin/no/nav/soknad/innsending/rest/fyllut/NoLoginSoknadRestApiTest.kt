package no.nav.soknad.innsending.rest.fyllut

import com.ninjasquad.springmockk.SpykBean
import io.mockk.clearAllMocks
import io.mockk.slot
import io.mockk.verify
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerAPITest
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.service.config.ConfigDefinition
import no.nav.soknad.innsending.service.config.ConfigService
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.utils.Api
import no.nav.soknad.innsending.utils.builders.SkjemaDokumentDtoV2TestBuilder
import no.nav.soknad.innsending.utils.builders.SkjemaDtoV2TestBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import java.util.*
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

	@SpykBean
	lateinit var soknadsmottaker: MottakerAPITest

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
	fun `skal sende inn soknad og handtere vedlegg med ulike statuser`() {
		val innsendingId = UUID.randomUUID().toString()

		val navId1 = "personal-id"
		val file1 = api.uploadNologinFile(vedleggId = navId1, innsendingId = innsendingId)
			.assertSuccess()
			.body

		val navId2 = "e9logo"
		api.uploadNologinFile(vedleggId = navId2, innsendingId = innsendingId)
			.assertSuccess()
			.body.let {
				assertNotNull(it.filId)
			}

		val navId3 = "dj5jkj"
		val file3 = api.uploadNologinFile(vedleggId = navId3, innsendingId = innsendingId)
			.assertSuccess()
			.body

		val vedleggLegitimasjon = SkjemaDokumentDtoV2TestBuilder(
			vedleggsnr = "K2",
			tittel = "Norsk pass",
			label = "Norsk pass",
			pakrevd = true,
			formioId = navId1,
			opplastingsStatus = OpplastingsStatusDto.LastetOpp,
			mimetype = Mimetype.applicationSlashPdf,
			filIdListe = listOf(file1.filId.toString()),
		).build()

		val vedleggSomSendesSenere = SkjemaDokumentDtoV2TestBuilder(
			vedleggsnr = "T4",
			tittel = "Kursbevis",
			label = "Kursbevis",
			pakrevd = true,
			formioId = navId2,
			opplastingsStatus = OpplastingsStatusDto.SendSenere,
			filIdListe = null,
		).build()

		val vedleggAnnenDokumentasjon = SkjemaDokumentDtoV2TestBuilder(
			vedleggsnr = "N6",
			tittel = "Annet",
			label = "Annen dokumentasjon",
			// propertyNavn = "annenDokumentasjon", <-- brukes ikke ved nologin
			pakrevd = false,
			formioId = navId3,
			opplastingsStatus = OpplastingsStatusDto.LastetOpp,
			mimetype = Mimetype.applicationSlashPdf,
			filIdListe = listOf(file3.filId.toString()),
		).build()

		val skjemaDto = SkjemaDtoV2TestBuilder()
			.medBrukerId("12345678901")
			.medInnsendingsId(innsendingId)
			.medVedlegg(listOf(vedleggLegitimasjon, vedleggSomSendesSenere, vedleggAnnenDokumentasjon))
			.build()

		val kvittering = api.sendInnNologinSoknad(skjemaDto)
			.assertSuccess()
			.body
		assertEquals(kvittering.hoveddokumentRef, null, "Skal ikke returnere hoveddokumentRef ved nologin")
		assertEquals(1, kvittering.skalEttersendes!!.size)
		assertEquals(2, kvittering.innsendteVedlegg!!.size)
		assertEquals(0, kvittering.skalSendesAvAndre!!.size)

		val slotSoknad = slot<DokumentSoknadDto>()
		val slotVedleggsliste = slot<List<VedleggDto>>()
		val slotAvsender = slot<AvsenderDto>()
		val slotBruker = slot<BrukerDto?>()
		verify(exactly = 1) {
			soknadsmottaker.sendInnSoknad(
				capture(slotSoknad),
				capture(slotVedleggsliste),
				capture(slotAvsender),
				captureNullable(slotBruker)
			)
		}

		assertEquals(innsendingId, slotSoknad.captured.innsendingsId)
		val innsendteDokumenter = slotVedleggsliste.captured
		assertEquals(5, innsendteDokumenter.size)

		val innsendtK2 = innsendteDokumenter.firstOrNull { it.vedleggsnr == vedleggLegitimasjon.vedleggsnr }
		assertNotNull(innsendtK2)

		val vedleggT4 = innsendteDokumenter.firstOrNull { it.vedleggsnr == vedleggSomSendesSenere.vedleggsnr }
		assertNull(vedleggT4)

		val innsendtN6 = innsendteDokumenter.firstOrNull { it.vedleggsnr == vedleggAnnenDokumentasjon.vedleggsnr }
		assertNotNull(innsendtN6)

		val innsendingskvittering = innsendteDokumenter.firstOrNull { it.vedleggsnr == Constants.KVITTERINGS_NR }
		assertNotNull(innsendingskvittering)

		val hoveddokumentListe = innsendteDokumenter.filter { it.erHoveddokument }
		assertEquals(2, hoveddokumentListe.size)
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

	@Test
	fun `skal sende inn søknad uten brukerId`() {
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
			.utenBrukerId()
			.medAvsender("Are Avsender")
			.medInnsendingsId(innsendingId)
			.medVedlegg(listOf(vedlegg1))
			.build()

		api.sendInnNologinSoknad(skjemaDto)
			.assertSuccess()

		val slotSoknad = slot<DokumentSoknadDto>()
		val slotVedleggsliste = slot<List<VedleggDto>>()
		val slotAvsender = slot<AvsenderDto>()
		val slotBruker = slot<BrukerDto?>()
		verify(exactly = 1) {
			soknadsmottaker.sendInnSoknad(
				capture(slotSoknad),
				capture(slotVedleggsliste),
				capture(slotAvsender),
				captureNullable(slotBruker)
			)
		}
		assertNull(slotBruker.captured)
		val actualAvsender = slotAvsender.captured
		assertNotNull(actualAvsender)
		assertEquals("Are Avsender", actualAvsender.navn)
	}

	@Test
	fun `innsending skal feile dersom hverken avsender eller bruker er satt`() {
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
			.utenBrukerId()
			.utenAvsender()
			.medInnsendingsId(innsendingId)
			.medVedlegg(listOf(vedlegg1))
			.build()

		api.sendInnNologinSoknad(skjemaDto)
			.assertClientError()
			.errorBody.let {
				assertEquals("Hverken bruker eller avsender er satt", it.message)
			}
	}

}
