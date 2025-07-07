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
import no.nav.soknad.innsending.service.fillager.FillagerService
import no.nav.soknad.innsending.utils.NoLoginApi
import no.nav.soknad.innsending.utils.TokenGenerator
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.VedleggDtoTestBuilder
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
		every { kodeverkService.getPoststed(any()) } answers { postnummerMap[firstArg()] }
	}

	@Value("\${server.port}")
	var serverPort: Int? = 9064


	@Test
	fun `test opprett og sendinn soknad uten vedlegg for uinnlogget bruker`() {
		// Gitt
		val token: String = TokenGenerator(mockOAuth2Server).lagAzureToken()

		val dokumentSoknadDto = DokumentSoknadDtoTestBuilder().withVisningsType(VisningsType.nologin).build()
		val hovedVedlegg = dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument && !it.erVariant }
		val hovedVedleggVariant = dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument && it.erVariant }
		val soknadPdfId = UUID.randomUUID().toString()
		val soknadJsonId = UUID.randomUUID().toString()

		every { fillagerService.lagreFil(any(), hovedVedlegg.uuid ?: UUID.randomUUID().toString(),dokumentSoknadDto.innsendingsId!!, any(),any() ) }	returns fillagerResponse(
				filId = soknadPdfId,
				vedleggId = hovedVedlegg.uuid ?: UUID.randomUUID().toString(),
				innsendingsId = dokumentSoknadDto.innsendingsId!!,
				filtype = "application/pdf",
				status = FilStatus.LASTET_OPP
			)
		every { fillagerService.lagreFil(any(), hovedVedleggVariant.uuid ?: UUID.randomUUID().toString(),dokumentSoknadDto.innsendingsId!!, any(),any() ) } returns FilMetadata(
				filId = soknadJsonId,
				vedleggId = hovedVedleggVariant.uuid ?: UUID.randomUUID().toString(),
				innsendingId = dokumentSoknadDto.innsendingsId!!,
				filtype = "application/json",
				filnavn = hovedVedleggVariant.tittel,
				storrelse = 1000,
				status = FilStatus.LASTET_OPP
			)

		// Når
		val innsendtSoknadResponse = api!!.createAndSendInSoknad(
			dokumentDto = dokumentSoknadDto,
			AvsenderDto(id = dokumentSoknadDto.brukerId, idType = AvsenderDto.IdType.FNR),
			BrukerDto(id = dokumentSoknadDto.brukerId, idType = BrukerDto.IdType.FNR),
			filDtos = emptyList(),
			envQualifier = EnvQualifier.preprodAltAnsatt
			)
			.assertSuccess()

		// Så
		assertTrue(innsendtSoknadResponse.statusCode == HttpStatus.OK )
		val kvitteringsDto = innsendtSoknadResponse.body
		assertEquals(0, kvitteringsDto.skalSendesAvAndre!!.size)
		assertTrue(kvitteringsDto.hoveddokumentRef == null)
	}



	@Test
	fun `test opprett og sendinn soknad med vedlegg for uinnlogget bruker`() {
		// Gitt
		val dokumentSoknadDto = DokumentSoknadDtoTestBuilder()
			.withVisningsType(VisningsType.nologin)
			.withVedlegg(VedleggDtoTestBuilder().asDefaultVedlegg().build()).build()
		val hovedVedlegg = dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument && !it.erVariant }
		val hovedVedleggVariant = dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument && it.erVariant }
		val vedlegg = dokumentSoknadDto.vedleggsListe.first { !it.erHoveddokument && !it.erVariant }
		val soknadPdfId = UUID.randomUUID().toString()
		val soknadJsonId = UUID.randomUUID().toString()
		val vedleggPdfId = UUID.randomUUID().toString()

		every { fillagerService.lagreFil(any(), hovedVedlegg.uuid ?: UUID.randomUUID().toString(),dokumentSoknadDto.innsendingsId!!, any(),any() ) }	returns fillagerResponse(
			filId = soknadPdfId,
			vedleggId = hovedVedlegg.uuid ?: UUID.randomUUID().toString(),
			innsendingsId = dokumentSoknadDto.innsendingsId!!,
			filtype = "application/pdf",
			status = FilStatus.LASTET_OPP
		)
		every { fillagerService.lagreFil(any(), hovedVedleggVariant.uuid ?: UUID.randomUUID().toString(),dokumentSoknadDto.innsendingsId!!, any(),any() ) } returns FilMetadata(
			filId = soknadJsonId,
			vedleggId = hovedVedleggVariant.uuid ?: UUID.randomUUID().toString(),
			innsendingId = dokumentSoknadDto.innsendingsId!!,
			filtype = "application/json",
			filnavn = hovedVedleggVariant.tittel,
			storrelse = 1000,
			status = FilStatus.LASTET_OPP
		)
		every { fillagerService.lagreFil(any(), vedlegg.uuid ?: UUID.randomUUID().toString(),dokumentSoknadDto.innsendingsId!!, any(),any() ) }	returns fillagerResponse(
			filId = vedleggPdfId,
			vedleggId = vedlegg.uuid ?: UUID.randomUUID().toString(),
			innsendingsId = dokumentSoknadDto.innsendingsId!!,
			filtype = "application/pdf",
			status = FilStatus.LASTET_OPP
		)
		every { fillagerService.hentFil(vedleggPdfId, dokumentSoknadDto.innsendingsId!!,any() ) }	returns hentfillageFilResponse(
			filId = vedleggPdfId,
			vedleggId = vedlegg.uuid!!,
			innsendingsId = dokumentSoknadDto.innsendingsId!!,
			filtype = "application/pdf",
		)

		// Når
		val innsendtSoknadResponse = api!!.createAndSendInSoknad(
			dokumentDto = dokumentSoknadDto,
			AvsenderDto(id = dokumentSoknadDto.brukerId, idType = AvsenderDto.IdType.FNR),
			BrukerDto(id = dokumentSoknadDto.brukerId, idType = BrukerDto.IdType.FNR),
			filDtos = listOf(NologinFilDto(vedleggRef = vedlegg.uuid!!, fileId = vedleggPdfId)),
			envQualifier = EnvQualifier.preprodAltAnsatt
		)
			.assertSuccess()

		// Så
		assertTrue(innsendtSoknadResponse.statusCode == HttpStatus.OK )
		val kvitteringsDto = innsendtSoknadResponse.body
		assertEquals(0, kvitteringsDto.skalSendesAvAndre!!.size)
		assertEquals(vedlegg.vedleggsnr, kvitteringsDto.innsendteVedlegg!!.first{it.vedleggsnr == vedlegg.vedleggsnr}.vedleggsnr)
		assertTrue(kvitteringsDto.hoveddokumentRef == null)
	}


	private fun fillagerResponse(filId: String, vedleggId: String, innsendingsId: String, filtype: String = "application/pdf", status: FilStatus = FilStatus.LASTET_OPP) = FilMetadata (
		filId = filId,
		vedleggId = vedleggId,
		innsendingId = innsendingsId,
		filnavn = "filnavn",
		storrelse = 1000,
		filtype = filtype,
		status = status
	)

	private fun hentfillageFilResponse(filId: String, vedleggId: String, innsendingsId: String, filtype: String) =
		Fil(
			innhold = byteArrayOf(1, 2, 3),
			metadata = FilMetadata (
				filId = filId,
				vedleggId = vedleggId,
				innsendingId = innsendingsId,
				filnavn = "filnavn",
				storrelse = 1000,
				filtype = filtype,
				status = FilStatus.LASTET_OPP,
			)
		)


}
