package no.nav.soknad.innsending.rest.fyllut


import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearAllMocks
import io.mockk.every
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenResponse
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.model.EnvQualifier
import no.nav.soknad.innsending.model.Mimetype
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.service.config.ConfigDefinition
import no.nav.soknad.innsending.service.config.ConfigService
import no.nav.soknad.innsending.service.fillager.Fil
import no.nav.soknad.innsending.service.fillager.FilMetadata
import no.nav.soknad.innsending.service.fillager.FilStatus
import no.nav.soknad.innsending.service.fillager.FillagerService
import no.nav.soknad.innsending.utils.NoLoginApi
import no.nav.soknad.innsending.utils.builders.SkjemaDokumentDtoV2TestBuilder
import no.nav.soknad.innsending.utils.builders.SkjemaDtoV2TestBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class NoLoginSoknadRestApiTest : ApplicationTest() {

	@MockkBean
	lateinit var oauth2TokenService: OAuth2AccessTokenService

	@Autowired
	lateinit var restTemplate: TestRestTemplate

	@Autowired
	lateinit var configService: ConfigService

	@MockkBean
	lateinit var fillagerService: FillagerService

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
		configService.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "on", "testuser")
	}

	@Value("\${server.port}")
	var serverPort: Int? = 9064


	@Test
	fun `test opprett og sendinn soknad med vedlegg for uinnlogget bruker`() {
		// Gitt
		val formioId = UUID.randomUUID().toString()
		val fileId = UUID.randomUUID().toString()

		val skjemaDokumentDto = SkjemaDokumentDtoV2TestBuilder(
			vedleggsnr = "W2",
			formioId = formioId,
			opplastingsStatus = OpplastingsStatusDto.LastetOpp,
			mimetype = Mimetype.applicationSlashPdf,
			filIdListe = listOf(fileId)
		).build()

		val skjemaDto = SkjemaDtoV2TestBuilder()
			.medBrukerId("12345678901")
			.medInnsendingsId(UUID.randomUUID().toString())
			.medVedlegg(skjemaDokumentDto)
			.medMellomlagringDager(1)
			.build()

		every {
			fillagerService.hentFil(
				filId = fileId,
				innsendingId = skjemaDto.innsendingsId!!,
				namespace = any()
			)
		} returns Fil(
			innhold = byteArrayOf(1, 2, 3),
			metadata = FilMetadata(
				filId = fileId,
				vedleggId = formioId,
				innsendingId = skjemaDto.innsendingsId!!,
				filnavn = "filnavn",
				storrelse = 1000,
				filtype = "application/pdf",
				status = FilStatus.LASTET_OPP,
			)
		)

		// Når
		val innsendtSoknadResponse = api!!.createAndSendInSoknad(
			dokumentDto = skjemaDto,
			envQualifier = EnvQualifier.preprodAltAnsatt
		)
			.assertSuccess()

		// Så
		assertTrue(innsendtSoknadResponse.statusCode == HttpStatus.OK)
		val kvitteringsDto = innsendtSoknadResponse.body
		assertEquals(0, kvitteringsDto.skalSendesAvAndre!!.size)
		assertTrue(kvitteringsDto.hoveddokumentRef == null)
	}


	@Test
	fun `skal avvise innsending dersom nologin main switch er av`() {
		// Gitt
		configService.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "off", "testuser")
		val formioId = UUID.randomUUID().toString()
		val fileId = UUID.randomUUID().toString()

		val skjemaDokumentDto = SkjemaDokumentDtoV2TestBuilder(
			formioId = formioId,
			opplastingsStatus = OpplastingsStatusDto.LastetOpp,
			mimetype = Mimetype.applicationSlashPdf,
			filIdListe = listOf(fileId)
		).build()

		val skjemaDto = SkjemaDtoV2TestBuilder()
			.medBrukerId("12345678901")
			.medInnsendingsId(UUID.randomUUID().toString())
			.medVedlegg(skjemaDokumentDto)
			.medMellomlagringDager(1)
			.build()

		every {
			fillagerService.hentFil(
				filId = fileId,
				innsendingId = skjemaDto.innsendingsId!!,
				namespace = any()
			)
		} returns Fil(
			innhold = byteArrayOf(1, 2, 3),
			metadata = FilMetadata(
				filId = fileId,
				vedleggId = formioId,
				innsendingId = skjemaDto.innsendingsId!!,
				filnavn = "filnavn",
				storrelse = 1000,
				filtype = "application/pdf",
				status = FilStatus.LASTET_OPP,
			)
		)

		// Når
		api!!.createAndSendInSoknad(
			dokumentDto = skjemaDto,
			envQualifier = EnvQualifier.preprodAltAnsatt
		) // Så
			.assertHttpStatus(HttpStatus.SERVICE_UNAVAILABLE)
	}


}
