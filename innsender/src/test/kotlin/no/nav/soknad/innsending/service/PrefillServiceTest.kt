package no.nav.soknad.innsending.service

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import kotlinx.coroutines.runBlocking
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenResponse
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.model.MaalgruppeType
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class PrefillServiceTest : ApplicationTest() {

	@MockkBean
	lateinit var subjectHandler: SubjectHandlerInterface

	@MockkBean
	lateinit var oauth2TokenService: OAuth2AccessTokenService

	@MockkBean
	lateinit var kodeverkService: KodeverkService

	@Autowired
	lateinit var prefillService: PrefillService

	val postnummerMap = mapOf(
		"7950" to "ABELVÆR",
		"3812" to "AKKERHAUGEN",
		"5575" to "AKSDAL",
		"7318" to "AGDENES",
	)

	@BeforeEach
	fun setup() {
		every { oauth2TokenService.getAccessToken(any()) } returns OAuth2AccessTokenResponse(access_token = "token")
		every { kodeverkService.getPoststed(any()) } answers { postnummerMap[firstArg()] }
	}

	@Test
	fun `Should get prefill data for PDL`() {
		// Given
		val properties = listOf("sokerFornavn", "sokerEtternavn", "sokerIdentifikasjonsnummer")
		val userId = "12128012345"

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
		val userId = "12128012345"

		// When
		val result = runBlocking { prefillService.getPrefillData(properties, userId) }

		// Then
		assertEquals("8361347234732292", result.sokerKontonummer)
	}

	@Test
	fun `Should get prefill data for Arena (maalgrupper)`() {
		// Given
		val properties = listOf("sokerMaalgruppe")
		val userId = "12128012345"

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
