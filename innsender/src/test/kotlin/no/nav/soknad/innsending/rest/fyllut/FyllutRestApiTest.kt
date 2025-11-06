package no.nav.soknad.innsending.rest.fyllut

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenResponse
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.service.FilService
import no.nav.soknad.innsending.service.KodeverkService
import no.nav.soknad.innsending.service.RepositoryUtils
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.mapping.tilleggsstonad.ungdomsprogram_reiseDaglig
import no.nav.soknad.innsending.util.models.*
import no.nav.soknad.innsending.utils.Api
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.TokenGenerator
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.SkjemaDokumentDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.SkjemaDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.VedleggDtoTestBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.http.*
import org.springframework.util.LinkedMultiValueMap
import java.time.LocalDate
import java.util.*
import kotlin.test.*


class FyllutRestApiTest : ApplicationTest() {

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
	lateinit var mockOAuth2Server: MockOAuth2Server

	val postnummerMap = mapOf(
		"7950" to "ABELVÆR",
		"3812" to "AKKERHAUGEN",
		"5575" to "AKSDAL",
		"7318" to "AGDENES",
	)

	var api: Api? = null

	@BeforeEach
	fun setup() {
		clearAllMocks()
		api = Api(restTemplate, serverPort!!, mockOAuth2Server)
		every { oauth2TokenService.getAccessToken(any()) } returns OAuth2AccessTokenResponse(access_token = "token")
		every { kodeverkService.getPoststed(any()) } answers { postnummerMap[firstArg()] }
	}

	@Value("\${server.port}")
	var serverPort: Int? = 9064


	@Test
	fun testOpprettSoknadPaFyllUtApi() {
		// Gitt
		val token: String = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val t7Vedlegg = SkjemaDokumentDtoTestBuilder(vedleggsnr = "T7").build()
		val n6Vedlegg = SkjemaDokumentDtoTestBuilder(vedleggsnr = "N6").build()
		val hoveddokument = SkjemaDokumentDtoTestBuilder( tittel = "Application for one-time grant at birth").asHovedDokument("NAV 10-07.41", withFile = false).build()
		val hoveddokumentVariant = SkjemaDokumentDtoTestBuilder( tittel = "Application for one-time grant at birth").asHovedDokumentVariant("NAV 10-07.41", withFile = true).build()

		val skjemaDto = SkjemaDtoTestBuilder( skjemanr="NAV 10-07.41", tittel = "Application for one-time grant at birth", vedleggsListe = listOf(t7Vedlegg, n6Vedlegg))
			.medHoveddokument(hoveddokument)
			.medHoveddokumentVariant(hoveddokumentVariant)
			.build()

		// Når
		val opprettetSoknadResponse = api!!.createSoknad(skjemaDto)
			.assertSuccess()

		val hoveddokumentMedFil = SkjemaDokumentDtoTestBuilder(tittel = "Application for one-time grant at birth").asHovedDokument("NAV 10-07.41", withFile = true).build()
		val utFyltSkjemaDto = SkjemaDtoTestBuilder( skjemanr="NAV 10-07.41", tittel = "Application for one-time grant at birth", vedleggsListe = listOf(t7Vedlegg, n6Vedlegg))
			.medHoveddokument(hoveddokumentMedFil)
			.medHoveddokumentVariant(hoveddokumentVariant)
			.build()
		api?.utfyltSoknad(opprettetSoknadResponse.body.innsendingsId!!, utFyltSkjemaDto)

		// Så
		testHentSoknadOgSendInn(opprettetSoknadResponse.body, token)
	}


	@Test
	fun testOpprettSoknadPaFyllUtApi_Ungdomsprogram() {
		// Gitt
		val skjemanr = ungdomsprogram_reiseDaglig
		val tittel = "Ungdomsprogrammet - daglig reise"
		val token: String = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val hoveddokument = SkjemaDokumentDtoTestBuilder( tittel = tittel).asHovedDokument(skjemanr, withFile = true).build()
		val hoveddokumentVariant = SkjemaDokumentDtoTestBuilder( tittel = tittel).asHovedDokumentVariant(skjemanr,
			withFile = true, file = "/__files/$ungdomsprogram_reiseDaglig-ungdomsprogrammet-reisedaglig.json").build()

		val skjemaDto = SkjemaDtoTestBuilder( skjemanr=skjemanr, tittel = tittel, tema = "TSR",
			hoveddokument	= hoveddokument, hoveddokumentVariant = hoveddokumentVariant, vedleggsListe = listOf())
			.build()

		// Når
		val opprettetSoknadResponse = api!!.createSoknad(skjemaDto)
			.assertSuccess()

		// Så
		///frontend/v1/sendInn/{innsendingsId}
		val sendInnRespons = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/sendInn/${opprettetSoknadResponse.body.innsendingsId}", HttpMethod.POST,
			HttpEntity<Unit>(Hjelpemetoder.createHeaders(token)), KvitteringsDto::class.java
		)

		assertTrue(sendInnRespons.statusCode == HttpStatus.OK && sendInnRespons.body != null)
		val kvitteringsDto = sendInnRespons.body
		assertEquals(0, kvitteringsDto!!.skalSendesAvAndre!!.size)
		assertTrue(kvitteringsDto.hoveddokumentRef != null)
	}


	@Test
	fun testUserNotificationOnSoknadCreation() {
		// Gitt
		val vedlegg = SkjemaDokumentDtoTestBuilder(vedleggsnr = "T7").build()
		val skjemaDto = SkjemaDtoTestBuilder(vedleggsListe = listOf(vedlegg)).build()

		// Når
		val responseBody = api!!.createSoknad(skjemaDto, envQualifier = EnvQualifier.preprodAltAnsatt)
			.assertSuccess()
			.body

		// Så
		val parameterSlot = slot<AddNotification>()
		verify(exactly = 1) { notificationPublisher.opprettBrukernotifikasjon(capture(parameterSlot)) }
		val notification = parameterSlot.captured

		assertEquals(responseBody.innsendingsId, notification.soknadRef.innsendingId)
		assertEquals(responseBody.innsendingsId, notification.soknadRef.groupId)
		assertEquals(responseBody.brukerId, notification.soknadRef.personId)
		assertEquals(false, notification.soknadRef.erSystemGenerert)
		assertEquals(false, notification.soknadRef.erEttersendelse)
		assertEquals(responseBody.tittel, notification.brukernotifikasjonInfo.notifikasjonsTittel)
		assertEquals(28, notification.brukernotifikasjonInfo.antallAktiveDager)
		assertTrue(
			notification.brukernotifikasjonInfo.lenke.contains("/oppsummering?"),
			"Link should point to summary page: ${notification.brukernotifikasjonInfo.lenke}"
		)
		assertTrue(
			notification.brukernotifikasjonInfo.lenke.contains("fyllut-preprod-alt.ansatt.dev.nav.no"),
			"Incorrect domain in link: ${notification.brukernotifikasjonInfo.lenke}"
		)
		assertEquals(0, notification.brukernotifikasjonInfo.eksternVarsling.size)
		assertNull(notification.brukernotifikasjonInfo.utsettSendingTil)
	}

	@Test
	fun testUserNotificationWithShorterMellomlagringOnSoknadCreation() {
		// Gitt
		val mellomlagringDager = 10
		val vedlegg = SkjemaDokumentDtoTestBuilder(vedleggsnr = "T7").build()
		val skjemaDto = SkjemaDtoTestBuilder(vedleggsListe = listOf(vedlegg), mellomlagringDager = mellomlagringDager, skalslettesdato = null).build()

		// Når
		val responseBody = api!!.createSoknad(skjemaDto)
			.assertSuccess()
			.body

		// Så
		val parameterSlot = slot<AddNotification>()
		verify(exactly = 1) { notificationPublisher.opprettBrukernotifikasjon(capture(parameterSlot)) }
		val notification = parameterSlot.captured

		assertEquals(responseBody.innsendingsId, notification.soknadRef.innsendingId)
		assertEquals(mellomlagringDager, notification.brukernotifikasjonInfo.antallAktiveDager)
	}

	@Test
	fun `Skal lage kvitteringsside`() {
		// Gitt
		val skjemaDto = SkjemaDtoTestBuilder().build()

		// Når
		val opprettetSoknadResponse = api?.createSoknad(skjemaDto)
		val innsendingsId = opprettetSoknadResponse?.body?.innsendingsId!!

		api?.utfyltSoknad(innsendingsId, skjemaDto)
		api?.sendInnSoknad(innsendingsId)

		val soknad = soknadService.hentSoknad(opprettetSoknadResponse.body!!.innsendingsId!!)
		val vedlegg = soknad.id?.let { repo.hentAlleVedleggGittSoknadsid(it) }

		// Så
		val kvittering = vedlegg?.kvittering
		assertEquals(3, vedlegg?.size) // Hoveddokument, hoveddokumentVariant og kvittering
		assertNotNull(kvittering)
	}

	private fun testHentSoknadOgSendInn(
		skjema: SkjemaDto,
		token: String
	) {

		// Hent søknaden opprettet fra FyllUt og kjør gjennom løp for opplasting av vedlegg og innsending av søknad
		val innsendingsId = skjema.innsendingsId
		assertNotNull(innsendingsId)

		val getRequestEntity = HttpEntity<Unit>(Hjelpemetoder.createHeaders(token))

		val getResponse = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${innsendingsId}", HttpMethod.GET,
			getRequestEntity, DokumentSoknadDto::class.java
		)

		Assertions.assertTrue(getResponse.body != null)
		val getSoknadDto = getResponse.body
		assertNotNull(getSoknadDto)
		assertEquals(4, getSoknadDto.vedleggsListe.size)
		assertFalse(getSoknadDto.kanLasteOppAnnet!!)
		assertFalse(getSoknadDto.erNavOpprettet!!)

		val vedleggT7 = getSoknadDto.vedleggsListe.first { it.vedleggsnr == "T7" }
		val patchVedleggT7 = PatchVedleggDto(
			tittel = null,
			opplastingsStatus = OpplastingsStatusDto.SendesAvAndre,
			opplastingsValgKommentar = "Sendes av min fastlege"
		)
		val patchRequestT7 = HttpEntity(patchVedleggT7, Hjelpemetoder.createHeaders(token))
		val patchResponseT7 = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${innsendingsId}/vedlegg/${vedleggT7.id}", HttpMethod.PATCH,
			patchRequestT7, VedleggDto::class.java
		)

		assertTrue(patchResponseT7.body != null)
		assertEquals(OpplastingsStatusDto.SendesAvAndre, patchResponseT7.body!!.opplastingsStatus)
		assertEquals("Sendes av min fastlege", patchResponseT7.body!!.opplastingsValgKommentar)

		val vedleggN6 = getSoknadDto.vedleggsListe.first { it.vedleggsnr == "N6" }
		val patchVedleggN6 = PatchVedleggDto(
			tittel = null,
			opplastingsStatus = OpplastingsStatusDto.IkkeValgt,
			opplastingsValgKommentar = null
		)
		val patchRequestN6 = HttpEntity(patchVedleggN6, Hjelpemetoder.createHeaders(token))
		val patchResponseN6 = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${innsendingsId}/vedlegg/${vedleggN6.id}", HttpMethod.PATCH,
			patchRequestN6, VedleggDto::class.java
		)

		assertTrue(patchResponseN6.body != null)
		assertEquals(OpplastingsStatusDto.IkkeValgt, patchResponseN6.body!!.opplastingsStatus)
		assertEquals(vedleggN6.id, patchResponseN6.body!!.id)

		val multipart = LinkedMultiValueMap<Any, Any>()
		multipart.add("file", ClassPathResource("/litenPdf.pdf"))

		val postFilRequestN6 = HttpEntity(multipart, Hjelpemetoder.createHeaders(token, MediaType.MULTIPART_FORM_DATA))
		val postFilResponseN6 = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${innsendingsId}/vedlegg/${vedleggN6.id}/fil", HttpMethod.POST,
			postFilRequestN6, FilDto::class.java
		)

		assertEquals(HttpStatus.CREATED, postFilResponseN6.statusCode)
		assertTrue(postFilResponseN6.body != null)
		assertEquals(Mimetype.applicationSlashPdf, postFilResponseN6.body!!.mimetype)

		///frontend/v1/sendInn/{innsendingsId}
		val sendInnRespons = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/sendInn/${innsendingsId}", HttpMethod.POST,
			HttpEntity<Unit>(Hjelpemetoder.createHeaders(token)), KvitteringsDto::class.java
		)

		assertTrue(sendInnRespons.statusCode == HttpStatus.OK && sendInnRespons.body != null)
		val kvitteringsDto = sendInnRespons.body
		assertEquals(1, kvitteringsDto!!.skalSendesAvAndre!!.size)
		assertTrue(kvitteringsDto.hoveddokumentRef != null)

		assertThrows<Exception> {
			restTemplate.exchange(
				"http://localhost:${serverPort}/frontend/v1/soknad/${innsendingsId}", HttpMethod.GET,
				HttpEntity<Unit>(Hjelpemetoder.createHeaders(token)), DokumentSoknadDto::class.java
			)
		}

		val hentFilURL = "http://localhost:${serverPort}/${kvitteringsDto.hoveddokumentRef}"
		val filRespons = restTemplate.exchange(
			hentFilURL, HttpMethod.GET,
			HttpEntity<Unit>(Hjelpemetoder.createHeaders(token, MediaType.APPLICATION_PDF)), ByteArray::class.java
		)
		assertEquals(HttpStatus.OK, filRespons.statusCode)
		assertTrue(filRespons.body != null)

	}

	@Test
	fun `Should update utfylt søknad and vedlegg with updated properties for språk and tittel, old vedlegg should be deleted`() {
		// Given
		val newSpraak = "en_gb"
		val newTittel = "Application for one-time grant at birth"
		val newVedleggstittel1 = "Birth certificate"
		val newVedleggstittel2 = "Marriage certificate"

		val dokumentSoknadDto = opprettSoknad() // med vedleggTittel1 og vedleggTittel2, begge med unik formioId satt
		val skjemanr = dokumentSoknadDto.skjemanr
		val innsendingsId = dokumentSoknadDto.innsendingsId!!

		val updatedT7 =
			SkjemaDokumentDtoTestBuilder(vedleggsnr = "T7", tittel = newVedleggstittel1).build() // Unik formioId settes
		val updatedN6 =
			SkjemaDokumentDtoTestBuilder(vedleggsnr = "N6", tittel = newVedleggstittel2).build() // Unik formioId settes
		val updatedHovedDokument =
			SkjemaDokumentDtoTestBuilder(tittel = newTittel).asHovedDokument(skjemanr).build() // formioId = null
		val updatedHovedDokumentVariant =
			SkjemaDokumentDtoTestBuilder(tittel = newTittel).asHovedDokumentVariant(skjemanr).build()

		val fraFyllUt = SkjemaDtoTestBuilder(
			skjemanr = dokumentSoknadDto.skjemanr,
			spraak = newSpraak,
			tittel = newTittel,
			vedleggsListe = listOf(updatedT7, updatedN6),
			hoveddokument = updatedHovedDokument,
			hoveddokumentVariant = updatedHovedDokumentVariant
		).build()

		// When
		val response = api?.utfyltSoknad(innsendingsId, fraFyllUt)
		val updatedSoknad = soknadService.hentSoknad(innsendingsId)

		// Then
		assertTrue(response != null)
		assertEquals(SoknadsStatusDto.Utfylt, updatedSoknad.status, "Status is set to utfylt")
		assertEquals(302, response.statusCode.value())
		assertEquals("http://localhost:3100/sendinn/${innsendingsId}", response.headers.location!!.toString())
		assertEquals(newTittel, updatedSoknad.tittel)
		assertEquals("en", updatedSoknad.spraak, "Språk is updated (gets converted to the first 2 letters)")
		assertEquals(
			4,
			updatedSoknad.vedleggsListe.size,
			"Hoveddokument, hoveddokumentVariant and two new vedlegg"
		)
		assertEquals(
			0,
			updatedSoknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.LastetOppIkkeRelevantLenger }.size,
			"Vedlegg should be deleted, not have status lastetOppIkkeRelevantLenger"
		)
		assertTrue(
			updatedSoknad.vedleggsListe.any { it.vedleggsnr == "T7" && it.tittel == newVedleggstittel1 },
			"Should have vedlegg T7"
		)
		assertTrue(
			updatedSoknad.vedleggsListe.any { it.vedleggsnr == "N6" && it.tittel == newVedleggstittel2 },
			"Should have vedlegg N6"
		)
		assertEquals(newTittel, updatedSoknad.hoveddokument!!.tittel, "Should have a new title for the hoveddokument")
		assertEquals(
			newTittel,
			updatedSoknad.hoveddokumentVariant!!.tittel,
			"Should have a new title for the hoveddokumentVariant"
		)
	}

	@Test
	fun `Should set status lastetOppIkkeRelevantLenger for vedlegg`() {
		// Given
		val skjemanr = "NAV 10-07.41"
		val vedleggsnr = "T7"
		val hoveddokument = SkjemaDokumentDtoTestBuilder(vedleggsnr = skjemanr).asHovedDokument(skjemanr).build()
		val hoveddokumentVariant =
			SkjemaDokumentDtoTestBuilder(vedleggsnr = skjemanr).asHovedDokumentVariant(skjemanr).build()
		val vedlegg = SkjemaDokumentDtoTestBuilder(vedleggsnr = vedleggsnr, tittel = "vedlegg1").build()

		val skjemaDto = SkjemaDtoTestBuilder(
			skjemanr = skjemanr,
			hoveddokument = hoveddokument,
			hoveddokumentVariant = hoveddokumentVariant
		).build()

		val skjemaDtoWithVedlegg = skjemaDto.copy(vedleggsListe = listOf(vedlegg))

		// When
		val opprettSoknadResponse = api?.createSoknad(skjemaDto)
		val innsendingsId = opprettSoknadResponse?.body?.innsendingsId!!

		// Complete søknad in fyllUt
		api?.utfyltSoknad(innsendingsId, skjemaDtoWithVedlegg)

		val savedSoknad = soknadService.hentSoknad(innsendingsId)
		val vedleggsId = savedSoknad.vedleggsListe.first { it.vedleggsnr == vedleggsnr }.id!!

		// Upload vedlegg in send-inn
		api!!.uploadFile(innsendingsId = innsendingsId, vedleggsId = vedleggsId)
			.assertHttpStatus(HttpStatus.CREATED)
		// Go back and remove vedlegg in fyllUt
		val utfyltResponse = api?.utfyltSoknad(innsendingsId, skjemaDto)

		val updatedSoknad = soknadService.hentSoknad(innsendingsId)

		// Then
		assertEquals(302, utfyltResponse!!.statusCode.value())
		assertEquals(
			"http://localhost:3100/sendinn/${innsendingsId}",
			utfyltResponse.headers.location!!.toString()
		)
		assertEquals(3, updatedSoknad.vedleggsListe.size)
		assertEquals(
			1,
			updatedSoknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.LastetOppIkkeRelevantLenger }.size
		)
		assertEquals(
			"vedlegg1",
			updatedSoknad.vedleggsListe.first { it.opplastingsStatus == OpplastingsStatusDto.LastetOppIkkeRelevantLenger }.tittel
		)
	}


	@Test
	fun `Should keep vedlegg from send-inn, even after updating from fyllUt and should delete old vedlegg not relevant anymore`() {
		// Given
		// Opprett søknaden i innsending-api med hoveddokument (inkludert variant) og vedleggsnr1 og vedleggsnr2
		val dokumentSoknadDto = opprettSoknad()
		val innsendingsId = dokumentSoknadDto.innsendingsId!!

		val t1Vedlegg = SkjemaDokumentDtoTestBuilder(vedleggsnr = "T1", tittel = "T1-vedleggstittel").build()

		// Bygg fyllut skjemaDto med hoveddokument (inkludert variant) og T1Vedlegg
		val fromFyllUt =
			SkjemaDtoTestBuilder(
				innsendingsId = innsendingsId, tittel = dokumentSoknadDto.tittel,
				skjemanr = dokumentSoknadDto.skjemanr).medVedlegg(t1Vedlegg).build()

		// N6 vedleggs fyllutId (aka formioId)
		val formioId = UUID.randomUUID().toString()

		// Legg til N6 vedlegg til fyllUt skjema, og fjern T1
		val fyllUtVedleggstittel = "N6-ny-vedleggstittel"
		val updatedN6Vedlegg = SkjemaDokumentDtoTestBuilder(
			vedleggsnr = "N6",
			tittel = fyllUtVedleggstittel,
			formioId = formioId
		).build()
		val updatedFyllUt =
			SkjemaDtoTestBuilder(innsendingsId = innsendingsId, tittel = dokumentSoknadDto.tittel, vedleggsListe = listOf(updatedN6Vedlegg), skjemanr = dokumentSoknadDto.skjemanr).build()

		// Oppdater søknaden i innsending-api med bruker opprettet N6 vedlegg
		val sendInnVedleggsTittel = "N6-fra-send-inn"
		val fromSendInn = PostVedleggDto(tittel = sendInnVedleggsTittel)

		// When
		// Complete søknad in fyllUt with N6 and T1 vedlegg. Vedlegg1 og vedlegg2 blir fjernet
		val utfyltResponse = api?.utfyltSoknad(innsendingsId, fromFyllUt)

		// Add N6 vedlegg i send-inn
		api!!.addVedlegg(innsendingsId, fromSendInn)
			.assertHttpStatus(HttpStatus.CREATED)

		// Go back to fyllUt and remove the T1 vedlegg. Keep N6 from send-inn, but also add one from fyllUt with different title
		val updatedUtfyltResponse = api?.utfyltSoknad(innsendingsId, updatedFyllUt)
		val updatedSoknad = soknadService.hentSoknad(innsendingsId)

		// Then
		assertTrue(utfyltResponse != null)
		assertTrue(updatedUtfyltResponse != null)

		assertEquals(302, utfyltResponse.statusCode.value())
		assertEquals(302, updatedUtfyltResponse.statusCode.value())
		assertEquals(
			"http://localhost:3100/sendinn/${innsendingsId}",
			updatedUtfyltResponse.headers.location!!.toString()
		)

		assertEquals(4, updatedSoknad.vedleggsListe.size)
		assertEquals(
			0,
			updatedSoknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.LastetOppIkkeRelevantLenger }.size,
			"Vedlegg should be deleted, not have status lastetOppIkkeRelevantLenger"
		)

		val n6FyllUtVedlegg =
			updatedSoknad.vedleggsListe.find { it.vedleggsnr == "N6" && it.tittel == fyllUtVedleggstittel }
		val n6SendInnVedlegg =
			updatedSoknad.vedleggsListe.find { it.vedleggsnr == "N6" && it.tittel == sendInnVedleggsTittel }

		assertNotNull(n6FyllUtVedlegg!!.formioId, "Vedlegg from fyllUt has formioId")
		assertNull(n6SendInnVedlegg!!.formioId, "Vedlegg from sendInn does not have formioId")

		assertFalse(
			updatedSoknad.vedleggsListe.any { it.vedleggsnr == "vedleggsnr1" || it.vedleggsnr == "vedleggsnr2" },
			"Should not have old vedlegg"
		)
		assertFalse(
			updatedSoknad.vedleggsListe.any { it.vedleggsnr == "T1" },
			"Should not have old vedlegg"
		)

	}

	@Test
	fun `Skal lagre filene i databasen`() {
		// Gitt
		val dokumentSoknadDto = opprettSoknad()
		val innsendingsId = dokumentSoknadDto.innsendingsId!!

		val fraFyllUt = SkjemaDtoTestBuilder(skjemanr = dokumentSoknadDto.skjemanr).build()

		// Når
		api?.updateSoknad(innsendingsId, fraFyllUt)
		val oppdatertSoknad = soknadService.hentSoknad(innsendingsId)

		val filer = oppdatertSoknad.vedleggsListe.flatMap {
			filService.hentFiler(
				oppdatertSoknad, innsendingsId,
				it.id!!, true
			)
		}

		// Så
		assertEquals(2, filer.size)
		filer.forEach {
			assertTrue(it.data!!.isNotEmpty())
		}
		filer.find { it.mimetype == Mimetype.applicationSlashPdf }?.let {
			assertTrue(it.filnavn!!.endsWith(".pdf"), "Skal være pdf-fil")
		}

		filer.find { it.mimetype == Mimetype.applicationSlashJson }?.let {
			assertTrue(it.filnavn!!.endsWith(".json"), "Skal være json-fil")
		}
	}

	@Test
	fun `Skal returnere riktig felter ved oppdatering av søknad`() {
		// Gitt
		val dokumentSoknadDto = opprettSoknad()
		val innsendingsId = dokumentSoknadDto.innsendingsId!!
		val nyttSpraak = "en_gb"

		val fraFyllUt = SkjemaDtoTestBuilder(skjemanr = dokumentSoknadDto.skjemanr, spraak = nyttSpraak).build()

		// Når
		val response = api?.updateSoknad(innsendingsId, fraFyllUt)
		val oppdatertSoknad = response?.body!!

		// Så
		assertEquals(dokumentSoknadDto.skjemanr, oppdatertSoknad.skjemanr)
		assertEquals(dokumentSoknadDto.innsendingsId, oppdatertSoknad.innsendingsId)
		assertEquals(dokumentSoknadDto.brukerId, oppdatertSoknad.brukerId)

		assertEquals("en", oppdatertSoknad.spraak, "Språk er oppdatert")
		assertNotEquals(dokumentSoknadDto.spraak, oppdatertSoknad.spraak, "Språk er oppdatert")

		val tidligereEndretDatoEpoch = dokumentSoknadDto.endretDato!!.toEpochSecond()
		val oppdatertEndretDatoEpoch = oppdatertSoknad.endretDato!!.toEpochSecond()

		assertTrue(dokumentSoknadDto.endretDato!!.isBefore(oppdatertSoknad.endretDato), "Skal returnere nyere endretDato")
		assertTrue(
			tidligereEndretDatoEpoch <= oppdatertEndretDatoEpoch && tidligereEndretDatoEpoch + 10 >= oppdatertEndretDatoEpoch,
			"Endret dato skal være innenfor 10 sekunder fra opprettelse i denne testen"
		)

	}

	@Test
	fun `Skal returnere error response hvis vedleggslisten ikke er tom`() {
		// Gitt
		val token: String = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val dokumentSoknadDto = opprettSoknad()
		val innsendingsId = dokumentSoknadDto.innsendingsId!!

		val vedlegg = SkjemaDokumentDtoTestBuilder().build()
		val fraFyllUt = SkjemaDtoTestBuilder(vedleggsListe = listOf(vedlegg)).build()

		val requestEntity = HttpEntity(fraFyllUt, Hjelpemetoder.createHeaders(token))

		// Når
		val response = restTemplate.exchange(
			"http://localhost:${serverPort}/fyllUt/v1/soknad/${innsendingsId}", HttpMethod.PUT,
			requestEntity, RestErrorResponseDto::class.java
		)
		val oppdatertSoknad = soknadService.hentSoknad(innsendingsId)

		// Så
		assertTrue(response != null)
		assertEquals(SoknadsStatusDto.Opprettet, oppdatertSoknad.status, "Status er satt til opprettet")
		assertEquals(500, response.statusCode.value())
		assertEquals(
			"Feil antall vedlegg. Skal kun ha hoveddokument og hoveddokumentVariant. Innsendt vedleggsliste skal være tom",
			response.body!!.message
		)

	}

	@Test
	fun `Skal hente opprettet søknad`() {
		// Gitt
		val skjemanr = "NAV 11-12.12"
		val dokumentSoknadDto = opprettSoknad(skjemanr)
		val innsendingsId = dokumentSoknadDto.innsendingsId!!

		// Når
		val response = api?.getSoknad(innsendingsId)

		val opprettetSoknad = response?.body!!

		// Så
		assertNotNull(response)
		assertEquals(200, response.statusCode.value())
		assertEquals(skjemanr, opprettetSoknad.skjemanr)
		assertEquals(2, opprettetSoknad.vedleggsListe?.size)
		assertNotNull(opprettetSoknad.hoveddokumentVariant.document, "HoveddokumentVariant skal ha fil")
		assertNull(opprettetSoknad.hoveddokument.document, "Hoveddokument skal ikke ha fil")

		val hovedDokument = dokumentSoknadDto.vedleggsListe.hovedDokument
		val hovedDokumentVariant = dokumentSoknadDto.vedleggsListe.hovedDokumentVariant

		assertEquals(hovedDokument!!.vedleggsnr, opprettetSoknad.hoveddokument.vedleggsnr, "Hoveddokument er riktig")
		assertEquals(
			hovedDokumentVariant!!.vedleggsnr,
			opprettetSoknad.hoveddokumentVariant.vedleggsnr,
			"HoveddokumentVariant er riktig"
		)
		assertNotNull(opprettetSoknad.endretDato)
		assertEquals(SoknadsStatusDto.Opprettet, opprettetSoknad.status, "Status er satt til opprettet")
		assertEquals(
			opprettetSoknad.skalSlettesDato?.toLocalDate(), dokumentSoknadDto.opprettetDato.plusDays(
				Constants.DEFAULT_LEVETID_OPPRETTET_SOKNAD
			).toLocalDate(), "SkalSlettesDato er satt til opprettetDato + 4 uker"
		)
	}

	@Test
	fun `Skal slette opprettet søknad`() {
		// Gitt
		val dokumentSoknadDto = opprettSoknad()
		val innsendingsId = dokumentSoknadDto.innsendingsId!!

		// Når
		val response = api?.deleteSoknad(innsendingsId)

		// Så
		assertTrue(response != null)
		assertEquals(200, response.statusCode.value())
		assertEquals("OK", response.body!!.status)
		assertEquals("Slettet soknad med id $innsendingsId", response.body!!.info)

		assertThrows<ResourceNotFoundException>("Søknaden skal ikke finnes") { soknadService.hentSoknad(innsendingsId) }
	}

	@Test
	fun `Should return error code with status 400 if user tries to update søknad that is sent in`() {
		// Given
		val skjemaDto = SkjemaDtoTestBuilder().build()

		// When
		val createdSoknad = api?.createSoknad(skjemaDto)
		val sentInSoknad = api?.sendInnSoknad(createdSoknad?.body?.innsendingsId!!)
		val response = api?.updateSoknadFail(sentInSoknad?.body?.innsendingsId!!, skjemaDto)

		// Then
		assertTrue(response != null)
		assertEquals(400, response.statusCode.value())
		assertEquals(ErrorCode.APPLICATION_SENT_IN_OR_DELETED.code, response.body?.errorCode)

	}


	@Test
	fun `Should not update opprettetDato when updating soknad`() {
		// Given
		val skjemaDto = SkjemaDtoTestBuilder().build()
		val createdSoknad = api?.createSoknad(skjemaDto)?.body

		val innsendingsId = createdSoknad?.innsendingsId!!
		val soknadBeforeUpdate = soknadService.hentSoknad(innsendingsId)

		// When
		api?.updateSoknad(innsendingsId, skjemaDto)
		val soknadAfterUpdate = soknadService.hentSoknad(innsendingsId)

		api?.utfyltSoknad(innsendingsId, skjemaDto)
		val soknadAfterUtfylt = soknadService.hentSoknad(innsendingsId)

		api?.sendInnSoknad(innsendingsId)
		val soknadAfterInnsending = soknadService.hentSoknad(innsendingsId)

		// Then
		assertEquals(soknadBeforeUpdate.opprettetDato, soknadAfterUpdate.opprettetDato)
		assertEquals(soknadBeforeUpdate.opprettetDato, soknadAfterUtfylt.opprettetDato)
		assertEquals(soknadBeforeUpdate.opprettetDato, soknadAfterInnsending.opprettetDato)
	}

	@Test
	fun `Skal redirecte ved eksisterende søknad gitt at force er false`() {
		// Gitt
		val dokumentSoknadDto = opprettSoknad(skjemanr = "NAV-redirect")

		val fraFyllUt = SkjemaDtoTestBuilder(skjemanr = dokumentSoknadDto.skjemanr).build()

		// Når
		val response = api?.createSoknadRedirect(fraFyllUt, false)

		// Så
		assertTrue(response != null)
		assertEquals(200, response.statusCode.value())
		assertEquals(ErrorCode.SOKNAD_ALREADY_EXISTS.code, response.body?.status)
	}

	@Test
	fun `Skal opprette søknad når force er true, selv om brukeren har en søknad med samme skjemanr`() {
		// Gitt
		val dokumentSoknadDto = opprettSoknad()
		val innsendingsId = dokumentSoknadDto.innsendingsId!!

		val fraFyllUt = SkjemaDtoTestBuilder(skjemanr = dokumentSoknadDto.skjemanr).build()

		// Når
		val response = api!!.createSoknad(fraFyllUt, true)
			.assertSuccess()

		// Så
		assertNotEquals(response.body.innsendingsId, innsendingsId, "Forventer ny innsendingsId")
	}

	@Test
	fun `Should return correct prefill-data from PDL`() {
		// Given
		val properties = "sokerFornavn,sokerEtternavn,sokerAdresser,sokerTelefonnummer"

		// When
		val response = api?.getPrefillData(properties)

		// Then
		assertTrue(response != null)
		assertEquals(200, response.statusCode.value())
		assertEquals("Ola", response.body?.sokerFornavn)
		assertEquals("Nordmann", response.body?.sokerEtternavn)
		assertEquals("Abelværvegen 1410", response.body?.sokerAdresser?.bostedsadresse?.adresse)
		assertEquals("Musdalsveien 25", response.body?.sokerAdresser?.oppholdsadresser?.get(0)?.adresse)
		assertEquals("Visjålivegen 585", response.body?.sokerAdresser?.kontaktadresser?.get(0)?.adresse)
		assertEquals("+4712345678", response.body?.sokerTelefonnummer)
	}

	@Test
	fun `Should return correct prefill-data from kontoregister`() {
		// Given
		val properties = "sokerKontonummer"

		// When
		val response = api?.getPrefillData(properties)

		// Then
		assertTrue(response != null)
		assertEquals(200, response.statusCode.value())
		assertEquals("8361347234732292", response.body?.sokerKontonummer)
	}

	@Test
	fun `Should return 400 from prefill-data if invalid prop is sent`() {
		// Given
		val properties = "sokerFornavn,sokerEtternavn,sokerInvalid"

		// When
		val response = api?.getPrefillDataFail(properties)

		// Then
		assertTrue(response != null)
		assertEquals(400, response.statusCode.value())
		assertEquals("'sokerInvalid' not a valid property", response.body?.message)
	}

	@Test
	fun `Should save and return skalSlettesDato`() {
		// Given
		val mellomlagringDager = 5
		val skalSlettesDato = LocalDate.now().plusDays(mellomlagringDager.toLong())

		val skjemaDto = SkjemaDtoTestBuilder(skalslettesdato = null, mellomlagringDager = mellomlagringDager).build()

		// When
		val createdSoknad = api?.createSoknad(skjemaDto)
		val getSoknad = api?.getSoknad(createdSoknad?.body?.innsendingsId!!)

		// Then
		assertEquals(
			skalSlettesDato,
			createdSoknad?.body?.skalSlettesDato?.toLocalDate()
		)
		assertEquals(
			skalSlettesDato,
			getSoknad?.body?.skalSlettesDato?.toLocalDate()
		)
	}

	@Test
	fun testOpprettSoknaderOgHentingYtelse() {
		// Gitt
		val token: String = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val t7Vedlegg = SkjemaDokumentDtoTestBuilder(vedleggsnr = "T7").build()
		val n6Vedlegg = SkjemaDokumentDtoTestBuilder(vedleggsnr = "N6").build()

		val noOfRepeats: Int = 100
		val soknader = mutableListOf<SkjemaDto>()
		repeat(noOfRepeats, {
			val soknad = SkjemaDtoTestBuilder(vedleggsListe = listOf(t7Vedlegg, n6Vedlegg)).build()

			// Når
			val opprettetSoknadResponse = api!!.createSoknad(soknad).assertSuccess()
			soknader.add(opprettetSoknadResponse.body)

		})


		// Så
		soknader.forEach { testHentSoknadOgSendInn(it, TokenGenerator(mockOAuth2Server).lagTokenXToken()) }

	}

	// Opprett søknad med et hoveddokument, en hoveddokumentvariant og to vedlegg
	private fun opprettSoknad(skjemanr: String = "NAV 08-21.05"): DokumentSoknadDto {
		val vedleggDtoPdf = VedleggDtoTestBuilder(vedleggsnr = skjemanr).asHovedDokument().build()
		val vedleggDtoJson = VedleggDtoTestBuilder(vedleggsnr = skjemanr).asHovedDokumentVariant().build()
		val vedleggDto1 = VedleggDtoTestBuilder(
			erHoveddokument = false,
			vedleggsnr = "vedleggsnr1",
			tittel = "vedleggTittel1",
			formioId = UUID.randomUUID().toString()
		).build()
		val vedleggDto2 = VedleggDtoTestBuilder(
			erHoveddokument = false,
			vedleggsnr = "vedleggsnr2",
			tittel = "vedleggTittel2",
			formioId = UUID.randomUUID().toString()
		).build()

		val vedleggsListe = listOf(vedleggDtoPdf, vedleggDtoJson, vedleggDto1, vedleggDto2)

		val dokumentSoknadDto =
			DokumentSoknadDtoTestBuilder(
				brukerId = TokenGenerator.subject,
				skjemanr = skjemanr,
				vedleggsListe = vedleggsListe
			).build()

		val innsendingsId = soknadService.opprettNySoknad(dokumentSoknadDto).innsendingsId!!

		return soknadService.hentSoknad(innsendingsId)
	}

}
