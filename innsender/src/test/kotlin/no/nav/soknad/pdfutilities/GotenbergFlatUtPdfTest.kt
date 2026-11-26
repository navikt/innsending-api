package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.PdfFlatteningOutcome
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.TestPdfSkjema
import no.nav.soknad.innsending.utils.TestPdfSkjema.Skjema
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

// Kjører mot ekte Gotenberg i testcontainer (se embedded/Gotenberg.kt)
class GotenbergFlatUtPdfTest : ApplicationTest() {

	@Autowired
	private lateinit var checkAndFormatPdf: CheckAndFormatPdf

	@Autowired
	private lateinit var innsenderMetrics: InnsenderMetrics

	@Test
	fun `flater ut utfylt skjema og beholder verdien som tekst`() {
		val skjema = TestPdfSkjema.lag(Skjema.UTFYLT_TEKSTFELT, antallSider = 2)
		val foer = innsenderMetrics.getPdfFlatteningCount(PdfFlatteningOutcome.GOTENBERG)

		val resultat = checkAndFormatPdf.flatUtPdf(skjema, 2)

		assertFalse(TestPdfSkjema.harSkjemafeltEllerWidgets(resultat))
		assertEquals(2, TestPdfSkjema.antallSider(resultat))
		assertTrue(TestPdfSkjema.tekst(resultat).contains(TestPdfSkjema.FELTVERDI))
		assertEquals(foer + 1, innsenderMetrics.getPdfFlatteningCount(PdfFlatteningOutcome.GOTENBERG))
	}

	@Test
	fun `flater ut skjema over 50 sider`() {
		val skjema = TestPdfSkjema.lag(Skjema.UTFYLT_TEKSTFELT, antallSider = 51)

		val resultat = checkAndFormatPdf.flatUtPdf(skjema, 51)

		assertFalse(TestPdfSkjema.harSkjemafeltEllerWidgets(resultat))
		assertEquals(51, TestPdfSkjema.antallSider(resultat))
		assertTrue(TestPdfSkjema.tekst(resultat).contains(TestPdfSkjema.FELTVERDI))
	}

	@Test
	fun `flater ut redigert Nav-skjema`() {
		val skjema = Hjelpemetoder.getBytesFromFile("/NAV 54-editert.pdf")
		val antallSider = TestPdfSkjema.antallSider(skjema)
		val foer = innsenderMetrics.getPdfFlatteningCount(PdfFlatteningOutcome.GOTENBERG)

		val resultat = checkAndFormatPdf.flatUtPdf(skjema, antallSider)

		assertFalse(TestPdfSkjema.harSkjemafeltEllerWidgets(resultat))
		assertEquals(antallSider, TestPdfSkjema.antallSider(resultat))
		assertEquals(foer + 1, innsenderMetrics.getPdfFlatteningCount(PdfFlatteningOutcome.GOTENBERG))
	}
}
