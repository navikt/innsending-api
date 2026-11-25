package no.nav.soknad.innsending.service

import io.mockk.mockk
import no.nav.soknad.innsending.consumerapis.saf.SafClient
import no.nav.soknad.innsending.consumerapis.saf.SafSelvbetjeningApiTmp
import no.nav.soknad.innsending.util.testpersonid
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class SafServiceTest {

	private val safClient = mockk<SafClient>()

	@Test
	fun hentInnsendteSoknaderForBrukerTest() {
		val brukerId = testpersonid
		val safService = SafService(SafSelvbetjeningApiTmp(), safClient)
		val innsendteSoknader = safService.hentInnsendteSoknader(brukerId)

		assertTrue(innsendteSoknader.isNotEmpty())
		assertEquals(5, innsendteSoknader.size)
		assertTrue(innsendteSoknader.all { it.innsendtVedleggDtos.isNotEmpty() })
		assertTrue(innsendteSoknader.all { it -> it.innsendtVedleggDtos.none { "L7".equals(it.vedleggsnr, true) } })
		assertTrue(innsendteSoknader.all { it -> it.innsendtVedleggDtos.none { it.vedleggsnr.startsWith("NAVe") } })
	}

	@Test
	fun brukerHarIngenInnsendteSoknaderTest() {
		val brukerId = "999999999999"
		val safService = SafService(SafSelvbetjeningApiTmp(), safClient)
		val innsendteSoknader = safService.hentInnsendteSoknader(brukerId)

		assertTrue(innsendteSoknader.isEmpty())
	}
}
