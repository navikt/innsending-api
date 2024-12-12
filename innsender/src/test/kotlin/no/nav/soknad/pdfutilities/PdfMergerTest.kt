package no.nav.soknad.pdfutilities

import junit.framework.TestCase
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.Hjelpemetoder.Companion.writeBytesToFile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

class PdfMergerTest : ApplicationTest() {

	@Autowired
	private lateinit var pdfMerger: PdfMergerInterface

	private val antallSider = AntallSider()
	private val validerer = VeraPDFValidator()

	@Test
	fun `sjekk at pdf filer blir merget`() {

		val pdfFiler = mutableListOf<ByteArray>()
		val antallFiler = 2
		for (i in 0 until antallFiler) {
			pdfFiler.add(Hjelpemetoder.getBytesFromFile("/litenPdf.pdf"))
		}

		val start = System.currentTimeMillis()
		val mergedPdf = pdfMerger.mergePdfer(pdfFiler)
		val ferdig = System.currentTimeMillis()
		println("Tid brukt for å merge ${pdfFiler.size} PDFer = ${ferdig - start}")

		val antallSider = antallSider.finnAntallSider(mergedPdf)
		assertEquals(antallFiler, antallSider)

		val erPdfa = validerer.validatePdf(mergedPdf)
		if (!erPdfa.isPdfACompliant) {
			println("Generert pdf er ikke PDF/A, indikerer at Gotenberg har feilet")
		}
		//TestCase.assertTrue(erPdfa.isPdfACompliant)

	}

	@Test
	fun `sjekk merging av mange pdf filer`() {
		val pdfFiler = mutableListOf<ByteArray>()
		val antallFiler = 10

		for (i in 0 until antallFiler) {
			pdfFiler.add(Hjelpemetoder.getBytesFromFile("/litenPdf.pdf"))
		}

		val start2 = System.currentTimeMillis()
		val mergedPdf2 = pdfMerger.mergePdfer(pdfFiler)
		val ferdig2 = System.currentTimeMillis()
		println("Tid brukt for å merge ${pdfFiler.size} PDFer = ${ferdig2 - start2}")

		assertEquals(pdfFiler.size, AntallSider().finnAntallSider(mergedPdf2))
		val erPdfa2 = validerer.validatePdf(mergedPdf2)
		TestCase.assertTrue(erPdfa2.isPdfACompliant)

	}


	@Test
	fun `sjekk merging av flere pdfer der en inneholder mange sider`() {

		val pdfFiler = mutableListOf<ByteArray>()
		val antallFiler = 2
		for (i in 0 until antallFiler) {
			pdfFiler.add(Hjelpemetoder.getBytesFromFile("/litenPdf.pdf"))
		}
		val storPdf = Hjelpemetoder.getBytesFromFile("/storPdf.pdf")
		pdfFiler.add(storPdf)

		val start = System.currentTimeMillis()
		val mergedPdf = pdfMerger.mergePdfer(pdfFiler)
		val ferdig = System.currentTimeMillis()
		println("Tid brukt for å merge ${pdfFiler.size} PDFer der en av PDFene består av mange sider = ${ferdig - start}")

		assertEquals(antallFiler + (antallSider.finnAntallSider(storPdf) ?: 0), AntallSider().finnAntallSider(mergedPdf))

	}

	@Test
	fun `sjekk merging av flere store pdfer`() {
		val pdfFiler = mutableListOf<ByteArray>()
		val antallFiler = 4
		for (i in 0 until antallFiler) {
			pdfFiler.add(Hjelpemetoder.getBytesFromFile("/storPdf.pdf"))
		}

		val start = System.currentTimeMillis()
		val mergedPdf = pdfMerger.mergePdfer(pdfFiler)
		val ferdig = System.currentTimeMillis()
		println("Tid brukt for å merge ${pdfFiler.size} PDFer der en av PDFene består av mange sider = ${ferdig - start}")
		Assertions.assertTrue(mergedPdf.size > 0)
		// Skriver til fil for manuell verifisering av sammenslåtte PDFer.
		//writeBytesToFile(mergedPdf, "/target/delme-merged.pdf")

	}

}
