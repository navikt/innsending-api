package no.nav.soknad.pdfutilities

import nl.altindag.log.LogCaptor
import no.nav.soknad.innsending.utils.Hjelpemetoder
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.Test

class KonverterTilPngTest {

	private val logCaptor: LogCaptor = LogCaptor.forRoot()

	@Test
	fun `Skal ikke logge feilmelding for JPEG2000 bilde i en PDF`() {
		// Gitt PDF med JPEG2000 bilde
		val filnavn = "jpeg2000"

		// Når
		konverterTilPng(filnavn)

		// Så
		assertTrue("Error logs skal være tom", logCaptor.errorLogs.isEmpty())
	}

	private fun konverterTilPng(filnavn: String) {
		val pdf = Hjelpemetoder.getBytesFromFile("/pdfs/$filnavn.pdf")
		val bilde = KonverterTilPng().konverterTilPng(pdf)

		assertNotNull(bilde)
	}
}
