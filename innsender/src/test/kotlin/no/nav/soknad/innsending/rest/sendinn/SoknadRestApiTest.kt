package no.nav.soknad.innsending.rest.sendinn

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.model.SoknadType
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.utils.Api
import no.nav.soknad.innsending.utils.TokenGenerator
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
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

class SoknadRestApiTest : ApplicationTest() {
	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var restTemplate: TestRestTemplate

	@Autowired
	lateinit var soknadService: SoknadService

	@Value("\${server.port}")
	var serverPort: Int? = 9064

	private val defaultUser = "12345678901"
	private val defaultSkjemanr = "NAV 55-00.60"

	var api: Api? = null

	@BeforeEach
	fun setup() {
		api = Api(restTemplate, serverPort!!, mockOAuth2Server)
	}

	@Test
	fun `Should create soknad and get a response`() {
		// When
		val response = api?.createSoknadForSkjemanr(defaultSkjemanr)

		// Then
		assertTrue(response?.body != null)
	}

	@Test
	fun `Should create soknad and retrieve it`() {
		// Given
		val postResponse = api?.createSoknadForSkjemanr(defaultSkjemanr)

		// When
		val getResponse = api?.getSoknadSendinn(postResponse?.body?.innsendingsId!!)

		// Then
		val getSoknadDto = getResponse?.body
		assertTrue(postResponse?.body != null)
		assertTrue(getResponse?.body != null)
		assertEquals(postResponse?.body?.innsendingsId, getSoknadDto!!.innsendingsId)
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
		val token = TokenGenerator(mockOAuth2Server).lagTokenXToken(defaultUser)

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

}
