package no.nav.soknad.innsending.rest.sendinn

import com.ninjasquad.springmockk.SpykBean
import io.mockk.clearAllMocks
import io.mockk.verify
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.model.EnvQualifier
import no.nav.soknad.innsending.model.SoknadType
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.utils.Api
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.SkjemaDokumentDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.SkjemaDtoTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import kotlin.test.assertNotEquals

class SoknadRestApiTest : ApplicationTest() {
	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var restTemplate: TestRestTemplate

	@Autowired
	lateinit var soknadService: SoknadService

	@SpykBean
	lateinit var notificationPublisher: PublisherInterface

	@Value("\${server.port}")
	var serverPort: Int? = 9064

	private val defaultUser = "12345678901"
	private val defaultSkjemanr = "NAV 55-00.60"

	var api: Api? = null

	@BeforeEach
	fun setup() {
		api = Api(restTemplate, serverPort!!, mockOAuth2Server)
		clearAllMocks()
	}

	@Test
	fun `Should fail creating soknad (old visningstype dokumentinnsending)`() {
		val errorBody = api!!.createSoknadForSkjemanr(defaultSkjemanr)
			.assertHttpStatus(HttpStatus.NOT_IMPLEMENTED)
			.errorBody
		assertEquals(
			"Operasjonen er ikke støttet",
			errorBody.message
		)
	}

	companion object {
		@JvmStatic
		fun hentSoknaderForSkjemanr() = listOf(
			Arguments.of(null, 3, 2, 1), // None, should return all (2 ettersendelse and 1 søknad)
			Arguments.of("ettersendelse", 2, 2, 0), // Ettersendelse, should return 2 ettersendelser
			Arguments.of("soknad", 1, 0, 1), // Søknad, should return 1 søknad
			Arguments.of("soknad,ettersendelse", 3, 2, 1) // Both, should return all (2 ettersendelse and 1 søknad)
		)
	}

	@ParameterizedTest
	@MethodSource("hentSoknaderForSkjemanr")
	fun `Should return response based on soknadstype query param`(
		queryParam: String?,
		expectedTotalSize: Int,
		expectedEttersendingSize: Int,
		expectedSoknadSize: Int
	) {
		// Given
		// 1 søknad and 2 ettersendingssøknader
		val soknad = DokumentSoknadDtoTestBuilder(brukerId = defaultUser).build()
		val opprettetSoknad = soknadService.opprettNySoknad(soknad)

		val ettersending =
			DokumentSoknadDtoTestBuilder(skjemanr = opprettetSoknad.skjemanr, brukerId = defaultUser).asEttersending().build()
		soknadService.opprettNySoknad(ettersending)

		val ettersending2 =
			DokumentSoknadDtoTestBuilder(skjemanr = opprettetSoknad.skjemanr, brukerId = defaultUser).asEttersending().build()
		soknadService.opprettNySoknad(ettersending2)

		val response = api?.getExistingSoknader(opprettetSoknad.skjemanr, queryParam)

		// Then
		val body = response?.body!!
		val responseEttersending = body.filter { it.soknadstype == SoknadType.ettersendelse }
		val responseSoknad = body.filter { it.soknadstype == SoknadType.soknad }

		assertEquals(HttpStatus.OK, response.statusCode)
		assertTrue(response.body != null)
		assertEquals(expectedTotalSize, response.body!!.size)
		assertEquals(expectedEttersendingSize, responseEttersending.size)
		assertEquals(expectedSoknadSize, responseSoknad.size)
	}

	@Test
	fun `Should create notification when ettersending is automatically created due to missing attachments`() {
		val createSoknadRequest = SkjemaDtoTestBuilder(vedleggsListe = listOf(
			SkjemaDokumentDtoTestBuilder(vedleggsnr = "T7").build(),
			SkjemaDokumentDtoTestBuilder(vedleggsnr = "N6").build()
		)).build()
		val innsendingsId = api!!.createSoknad(createSoknadRequest, envQualifier = EnvQualifier.preprodAnsatt)
			.assertSuccess().body.innsendingsId!!
		val soknad = api!!.getSoknadSendinn(innsendingsId)
			.assertSuccess().body

		val vedleggsId = soknad.vedleggsListe.first { it.vedleggsnr == "N6" }.id
		val fil = Hjelpemetoder.getBytesFromFile("/litenPdf.pdf")
		api!!.uploadFile(innsendingsId, vedleggsId!!, fil)

		val innsendingskvittering = api!!.sendInnSoknad(innsendingsId, EnvQualifier.preprodAnsatt)
			.assertSuccess().body
		assertEquals(1, innsendingskvittering.skalEttersendes?.size)

		val notifications = mutableListOf<AddNotification>()
		verify(exactly = 2) { notificationPublisher.opprettBrukernotifikasjon(capture(notifications)) }
		verify(exactly = 1) { notificationPublisher.avsluttBrukernotifikasjon(any()) }

		val notificationForInitialSoknad = notifications.first()
		assertFalse(notificationForInitialSoknad.soknadRef.erEttersendelse)
		assertEquals(false, notificationForInitialSoknad.soknadRef.erSystemGenerert)
		assertEquals(innsendingsId, notificationForInitialSoknad.soknadRef.innsendingId)
		assertEquals(innsendingsId, notificationForInitialSoknad.soknadRef.groupId)
		assertNull(notificationForInitialSoknad.brukernotifikasjonInfo.utsettSendingTil)
		assertTrue(
			notificationForInitialSoknad.brukernotifikasjonInfo.lenke.contains("ansatt.dev.nav.no"),
			"Unexpected link: ${notificationForInitialSoknad.brukernotifikasjonInfo.lenke}"
		)

		val notificationForEttersending = notifications.last()
		assertTrue(notificationForEttersending.soknadRef.erEttersendelse)
		assertEquals(true, notificationForEttersending.soknadRef.erSystemGenerert)
		assertNotNull(notificationForEttersending.soknadRef.innsendingId)
		assertNotEquals(innsendingsId, notificationForEttersending.soknadRef.innsendingId)
		assertEquals(innsendingsId, notificationForEttersending.soknadRef.groupId)
		assertNotNull(notificationForEttersending.brukernotifikasjonInfo.utsettSendingTil)
		assertTrue(
			notificationForEttersending.brukernotifikasjonInfo.lenke.contains("ansatt.dev.nav.no"),
			"Unexpected link: ${notificationForEttersending.brukernotifikasjonInfo.lenke}"
		)
	}

}
