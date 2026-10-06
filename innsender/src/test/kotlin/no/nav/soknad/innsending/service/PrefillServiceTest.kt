package no.nav.soknad.innsending.service

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import kotlinx.coroutines.runBlocking
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.config.AccessToken
import no.nav.soknad.innsending.config.PdlClientConfig
import no.nav.soknad.innsending.consumerapis.kontoregister.KontoregisterInterface
import no.nav.soknad.innsending.model.MaalgruppeType
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.utils.TokenGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean

class PrefillServiceTest : ApplicationTest() {

	@MockkBean
	lateinit var subjectHandler: SubjectHandlerInterface

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@MockkBean
	lateinit var accessToken: AccessToken

	@MockitoSpyBean
	lateinit var pdlClientConfig: PdlClientConfig

	@MockkBean
	lateinit var kodeverkService: KodeverkService

	@MockkBean
	lateinit var kontoregisterService: KontoregisterInterface


	@Autowired
	lateinit var prefillService: PrefillService

	val postnummerMap = mapOf(
		"7950" to "ABELVÆR",
		"3812" to "AKKERHAUGEN",
		"5575" to "AKSDAL",
		"7318" to "AGDENES",
	)

	private val userId = "12128012345"

	@BeforeEach
	fun setup() {
		val (token, jwt) = TokenGenerator(mockOAuth2Server).lagTokenXTokenAndJwt(fnr = userId)
		`when`(tokenxJwtDecoder.decode(anyString())).thenReturn(jwt)
		every { accessToken.getAccessToken("tokenx-pdl", null) } returns token
		every { kodeverkService.getPoststed(any()) } answers { postnummerMap[firstArg()] }
		every { kontoregisterService.getKontonummer() } returns "8361347234732292"
	}

	@Test
	fun `Should get prefill data for PDL`() {
		// Given
		val properties = listOf("sokerFornavn", "sokerEtternavn", "sokerIdentifikasjonsnummer")

		// When
		val result = runBlocking { prefillService.getPrefillData(properties, userId) }

		// Then
		assertEquals("Ola", result.sokerFornavn)
		assertEquals("Nordmann", result.sokerEtternavn)
		assertEquals(userId, result.sokerIdentifikasjonsnummer)
	}

	@Test
	fun `Should get prefill data for kontoregister`() {
		// Given
		val properties = listOf("sokerKontonummer")

		// When
		val result = runBlocking { prefillService.getPrefillData(properties, userId) }

		// Then
		assertEquals("8361347234732292", result.sokerKontonummer)
	}

	@Test
	fun `Should get prefill data for Arena (maalgrupper)`() {
		// Given
		val properties = listOf("sokerMaalgruppe")

		every { subjectHandler.getUserIdFromToken() } returns userId

		// When
		val result = runBlocking { prefillService.getPrefillData(properties, userId) }

		// Then
		assertEquals(MaalgruppeType.NEDSARBEVN, result.sokerMaalgruppe?.maalgruppetype)
		assertEquals("Person med nedsatt arbeidsevne pga. sykdom", result.sokerMaalgruppe?.maalgruppenavn)
		assertEquals("2023-01-01", result.sokerMaalgruppe?.gyldighetsperiode?.fom.toString())
	}

	@Test
	fun `Should get prefill data for addresses and enrich with poststed`() {
		// Given
		val properties = listOf("sokerAdresser")
		val userId = "12128012345"

		// When
		val result = runBlocking { prefillService.getPrefillData(properties, userId) }

		// Then
		assertEquals("ABELVÆR", result.sokerAdresser?.bostedsadresse?.bySted)

		assertEquals(3, result.sokerAdresser?.kontaktadresser?.size)
		result.sokerAdresser?.kontaktadresser?.forEach { address ->
			if (address.landkode == "NOR" && address.postnummer != null) assertEquals(
				postnummerMap[address.postnummer],
				address.bySted
			)
		}

		assertEquals(2, result.sokerAdresser?.oppholdsadresser?.size)
		result.sokerAdresser?.oppholdsadresser?.forEach { address ->
			if (address.landkode == "NOR" && address.postnummer != null) assertEquals(
				postnummerMap[address.postnummer],
				address.bySted
			)
		}
	}
}
