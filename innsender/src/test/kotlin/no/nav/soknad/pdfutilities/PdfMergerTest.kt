package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.Hjelpemetoder.Companion.writeBytesToFile
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled

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

		checkPdfACompliance(mergedPdf)

	}


	@Test
	fun `sjekk at mellomstore pdf filer blir merget`() {

		val pdfFiler = mutableListOf<ByteArray>()
		val antallFiler = 4
		val fil = Hjelpemetoder.getBytesFromFile("/mellomstor.pdf")
		for (i in 0 until antallFiler) {
			pdfFiler.add(fil)
		}
		val sideSum = antallFiler * (antallSider.finnAntallSider(fil) ?: 0)
		val start = System.currentTimeMillis()
		val mergedPdf = pdfMerger.mergePdfer(pdfFiler)
		val ferdig = System.currentTimeMillis()
		println("Tid brukt for å merge ${pdfFiler.size} PDFer = ${ferdig - start}")

		val antallSider = antallSider.finnAntallSider(mergedPdf)
		assertEquals(sideSum, antallSider)

		checkPdfACompliance(mergedPdf)
	}

	@Test
	fun `sjekk at mellomstore pdf filer konvert fra jpg blir merget`() {

		val pdfFiler = mutableListOf<ByteArray>()
		val antallFiler = 4
		val fil = Hjelpemetoder.getBytesFromFile("/mellomstor-fra-jpg.pdf")

		for (i in 0 until antallFiler) {
			pdfFiler.add(fil)
		}

		val start = System.currentTimeMillis()
		val mergedPdf = pdfMerger.mergePdfer(pdfFiler)
		val ferdig = System.currentTimeMillis()
		println("Tid brukt for å merge ${pdfFiler.size} PDFer = ${ferdig - start}")

		val antallSider = antallSider.finnAntallSider(mergedPdf)
		assertEquals(antallFiler, antallSider)

		checkPdfACompliance(mergedPdf)
	}

	@Test
	fun `sjekk merging av mange pdf filer`() {
		val pdfFiler = mutableListOf<ByteArray>()
		val antallFiler = 30
		val fil = Hjelpemetoder.getBytesFromFile("/litenPdf.pdf")

		for (i in 0 until antallFiler) {
			pdfFiler.add(fil)
		}

		val start2 = System.currentTimeMillis()
		val mergedPdf2 = pdfMerger.mergePdfer(pdfFiler)
		val ferdig2 = System.currentTimeMillis()
		println("Tid brukt for å merge ${pdfFiler.size} PDFer = ${ferdig2 - start2}")

		assertEquals(pdfFiler.size, AntallSider().finnAntallSider(mergedPdf2))
		checkPdfACompliance(mergedPdf2)

	}


	@Test
	fun `sjekk merging av flere pdfer der en inneholder mange sider`() {

		val pdfFiler = mutableListOf<ByteArray>()
		val antallFiler = 2
		val fil = Hjelpemetoder.getBytesFromFile("/litenPdf.pdf")
		for (i in 0 until antallFiler) {
			pdfFiler.add(fil)
		}
		val storPdf = Hjelpemetoder.getBytesFromFile("/storPdf.pdf")
		pdfFiler.add(storPdf)

		val start = System.currentTimeMillis()
		val mergedPdf = pdfMerger.mergePdfer(pdfFiler)
		val ferdig = System.currentTimeMillis()
		println("Tid brukt for å merge ${pdfFiler.size} PDFer der en av PDFene består av mange sider = ${ferdig - start}")

		assertEquals(antallFiler + (antallSider.finnAntallSider(storPdf) ?: 0), AntallSider().finnAntallSider(mergedPdf))
		checkPdfACompliance(mergedPdf)

	}

	@Test
	@Disabled("StorPdf.pdf er 47,9MB. Ikke relevant så lenge 50MB er maksimum størrelse på et vedlegg.")
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
		assertTrue(mergedPdf.size > 0)
		checkPdfACompliance(mergedPdf)

	}

	private fun checkPdfACompliance(pdf: ByteArray) {
		// Skriver til fil for manuell verifisering av sammenslåtte PDFer.
		//writeBytesToFile(mergedPdf, "delme-merged.pdf")
		val erPdfa2 = validerer.validatePdf(pdf)
		if (erPdfa2.isPdfACompliant) {
			println("Filen er PDF/A compliant")
		} else {
			println("Filen er ikke PDF/A compliant")
		}
		// Foreløpi slått av merging av PDFer med PDF/A resultat
		//assertTrue(erPdfa2.isPdfACompliant)
	}

}
