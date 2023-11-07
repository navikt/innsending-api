package no.nav.soknad.innsending.service

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import kotlinx.coroutines.runBlocking
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class PrefillServiceTest : ApplicationTest() {

	@MockkBean
	lateinit var subjectHandler: SubjectHandlerInterface

	@Autowired
	lateinit var prefillService: PrefillService

	@Test
	fun `Should get prefill data for PDL`() {
		// Given
		val properties = listOf("sokerFornavn", "sokerEtternavn")
		val userId = "12128012345"

		// When
		val result = runBlocking { prefillService.getPrefillData(properties, userId) }

		// Then
		assertEquals("Ola", result.sokerFornavn)
		assertEquals("Nordmann", result.sokerEtternavn)
	}

	@Test
	fun `Should get prefill data for Arena`() {
		// Given
		val properties = listOf("sokerMaalgrupper")
		val userId = "12128012345"

		every { subjectHandler.getUserIdFromToken() } returns userId

		// When
		val result = runBlocking { prefillService.getPrefillData(properties, userId) }

		// Then
		assertEquals("NEDSARBEVN", result.sokerMaalgrupper?.get(0))
	}

}
