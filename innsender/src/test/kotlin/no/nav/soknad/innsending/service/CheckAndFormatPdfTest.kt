package no.nav.soknad.innsending.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.PdfFlatteningOutcome
import no.nav.soknad.innsending.utils.TestPdfSkjema
import no.nav.soknad.innsending.utils.TestPdfSkjema.Skjema
import no.nav.soknad.pdfutilities.CheckAndFormatPdf
import no.nav.soknad.pdfutilities.FileToPdfInterface
import no.nav.soknad.pdfutilities.PdfMerger
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CheckAndFormatPdfTest {

	private val pdfConverter = mockk<FileToPdfInterface>()
	private val metrics = InnsenderMetrics(PrometheusRegistry())
	private val checkAndFormatPdf = CheckAndFormatPdf(pdfConverter, PdfMerger(pdfConverter), metrics)

	@Test
	fun `bruker resultatet fra Gotenberg naar det er gyldig`() {
		val skjema = TestPdfSkjema.lag(Skjema.UTFYLT_TEKSTFELT, antallSider = 2)
		val utflatet = TestPdfSkjema.lag(Skjema.INGEN_ACROFORM, antallSider = 2)
		every { pdfConverter.flattenPdf(skjema, any()) } returns utflatet

		val resultat = checkAndFormatPdf.flatUtPdf(skjema, 2)

		assertArrayEquals(utflatet, resultat)
		assertEquals(1.0, metrics.getPdfFlatteningCount(PdfFlatteningOutcome.GOTENBERG))
	}

	@Test
	fun `fjerner widgets uten utseende som Gotenberg etterlater`() {
		val skjema = TestPdfSkjema.lag(Skjema.UTFYLT_TEKSTFELT, antallSider = 2)
		every { pdfConverter.flattenPdf(any(), any()) } returns TestPdfSkjema.lag(Skjema.WIDGET_UTEN_UTSEENDE, antallSider = 2)

		val resultat = checkAndFormatPdf.flatUtPdf(skjema, 2)

		assertFalse(TestPdfSkjema.harSkjemafeltEllerWidgets(resultat))
		assertEquals(2, TestPdfSkjema.antallSider(resultat))
		assertEquals(1.0, metrics.getPdfFlatteningCount(PdfFlatteningOutcome.GOTENBERG))
	}

	@Test
	fun `fjerner aldri widgets med utseende, men bildekonverterer`() {
		val skjema = TestPdfSkjema.lag(Skjema.UTFYLT_TEKSTFELT)
		every { pdfConverter.flattenPdf(any(), any()) } returns TestPdfSkjema.lag(Skjema.WIDGET_MED_UTSEENDE)

		val resultat = checkAndFormatPdf.flatUtPdf(skjema, 1)

		assertFalse(TestPdfSkjema.harSkjemafeltEllerWidgets(resultat))
		assertEquals(1.0, metrics.getPdfFlatteningCount(PdfFlatteningOutcome.FALLBACK_INVALID))
	}

	@Test
	fun `sender widgets uten AcroForm til Gotenberg`() {
		val pdf = TestPdfSkjema.lag(Skjema.WIDGET_MED_UTSEENDE)
		val utflatet = TestPdfSkjema.lag(Skjema.INGEN_ACROFORM)
		every { pdfConverter.flattenPdf(pdf, any()) } returns utflatet

		val resultat = checkAndFormatPdf.flatUtPdf(pdf, 1)

		assertArrayEquals(utflatet, resultat)
		assertEquals(1.0, metrics.getPdfFlatteningCount(PdfFlatteningOutcome.GOTENBERG))
	}

	@Test
	fun `registrerer failed og kaster videre naar baade Gotenberg og bildekonvertering feiler`() {
		val skjema = TestPdfSkjema.lag(Skjema.UTFYLT_TEKSTFELT, antallSider = 2)
		every { pdfConverter.flattenPdf(any(), any()) } throws RuntimeException("503")
		val feilendeMerger = mockk<PdfMerger>()
		every { feilendeMerger.mergeWithPDFBox(any()) } throws RuntimeException("merge feilet")
		val medFeilendeFallback = CheckAndFormatPdf(pdfConverter, feilendeMerger, metrics)

		assertThrows<RuntimeException> { medFeilendeFallback.flatUtPdf(skjema, 2) }

		assertEquals(1.0, metrics.getPdfFlatteningCount(PdfFlatteningOutcome.FAILED))
	}

	@Test
	fun `bildekonverterer naar Gotenberg feiler`() {
		val skjema = TestPdfSkjema.lag(Skjema.UTFYLT_TEKSTFELT, antallSider = 2)
		every { pdfConverter.flattenPdf(any(), any()) } throws
			IllegalActionException("Got 503", null, ErrorCode.TYPE_DETECTION_OR_CONVERSION_ERROR)

		val resultat = checkAndFormatPdf.flatUtPdf(skjema, 2)

		assertFalse(TestPdfSkjema.harSkjemafeltEllerWidgets(resultat))
		assertEquals(2, TestPdfSkjema.antallSider(resultat))
		assertEquals(1.0, metrics.getPdfFlatteningCount(PdfFlatteningOutcome.FALLBACK_ERROR))
		// Fallback skal slå sammen sidene lokalt, ikke kalle Gotenberg på nytt
		verify(exactly = 0) { pdfConverter.mergePdfs(any(), any()) }
	}

	@Test
	fun `bildekonverterer naar Gotenberg returnerer PDF med skjemafelt`() {
		val skjema = TestPdfSkjema.lag(Skjema.UTFYLT_TEKSTFELT)
		every { pdfConverter.flattenPdf(any(), any()) } returns skjema

		val resultat = checkAndFormatPdf.flatUtPdf(skjema, 1)

		assertFalse(TestPdfSkjema.harSkjemafeltEllerWidgets(resultat))
		assertEquals(1.0, metrics.getPdfFlatteningCount(PdfFlatteningOutcome.FALLBACK_INVALID))
	}

	@Test
	fun `bildekonverterer naar Gotenberg endrer sideantallet`() {
		val skjema = TestPdfSkjema.lag(Skjema.UTFYLT_TEKSTFELT, antallSider = 2)
		every { pdfConverter.flattenPdf(any(), any()) } returns TestPdfSkjema.lag(Skjema.INGEN_ACROFORM, antallSider = 1)

		val resultat = checkAndFormatPdf.flatUtPdf(skjema, 2)

		assertEquals(2, TestPdfSkjema.antallSider(resultat))
		assertFalse(TestPdfSkjema.harSkjemafeltEllerWidgets(resultat))
		assertEquals(1.0, metrics.getPdfFlatteningCount(PdfFlatteningOutcome.FALLBACK_INVALID))
	}

	@Test
	fun `bildekonverterer naar Gotenberg returnerer noe som ikke er PDF`() {
		val skjema = TestPdfSkjema.lag(Skjema.UTFYLT_TEKSTFELT)
		every { pdfConverter.flattenPdf(any(), any()) } returns "ikke en pdf".toByteArray()

		val resultat = checkAndFormatPdf.flatUtPdf(skjema, 1)

		assertFalse(TestPdfSkjema.harSkjemafeltEllerWidgets(resultat))
		assertEquals(1.0, metrics.getPdfFlatteningCount(PdfFlatteningOutcome.FALLBACK_INVALID))
	}

	@Test
	fun `beholder originalen over 50 sider naar Gotenberg feiler`() {
		val skjema = TestPdfSkjema.lag(Skjema.UTFYLT_TEKSTFELT, antallSider = 51)
		every { pdfConverter.flattenPdf(any(), any()) } throws RuntimeException("timeout")

		val resultat = checkAndFormatPdf.flatUtPdf(skjema, 51)

		assertArrayEquals(skjema, resultat)
		assertEquals(1.0, metrics.getPdfFlatteningCount(PdfFlatteningOutcome.UNFLATTENED))
	}

	@Test
	fun `flater ut over 50 sider med Gotenberg`() {
		val skjema = TestPdfSkjema.lag(Skjema.UTFYLT_TEKSTFELT, antallSider = 51)
		val utflatet = TestPdfSkjema.lag(Skjema.INGEN_ACROFORM, antallSider = 51)
		every { pdfConverter.flattenPdf(any(), any()) } returns utflatet

		val resultat = checkAndFormatPdf.flatUtPdf(skjema, 51)

		assertArrayEquals(utflatet, resultat)
		assertEquals(1.0, metrics.getPdfFlatteningCount(PdfFlatteningOutcome.GOTENBERG))
	}

	@Test
	fun `kaller ikke Gotenberg for PDF uten AcroForm`() {
		val pdf = TestPdfSkjema.lag(Skjema.INGEN_ACROFORM)

		val resultat = checkAndFormatPdf.flatUtPdf(pdf, 1)

		assertArrayEquals(pdf, resultat)
		assertEquals(1.0, metrics.getPdfFlatteningCount(PdfFlatteningOutcome.SKIPPED))
		verify(exactly = 0) { pdfConverter.flattenPdf(any(), any()) }
	}

	@Test
	fun `kaller ikke Gotenberg for tomt AcroForm`() {
		val pdf = TestPdfSkjema.lag(Skjema.TOM_ACROFORM)

		val resultat = checkAndFormatPdf.flatUtPdf(pdf, 1)

		assertArrayEquals(pdf, resultat)
		assertEquals(1.0, metrics.getPdfFlatteningCount(PdfFlatteningOutcome.SKIPPED))
		verify(exactly = 0) { pdfConverter.flattenPdf(any(), any()) }
	}
}
