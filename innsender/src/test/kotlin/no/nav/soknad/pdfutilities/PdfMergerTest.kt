package no.nav.soknad.pdfutilities

import junit.framework.TestCase
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.Hjelpemetoder.Companion.writeBytesToFile
import no.nav.soknad.pdfutilities.gotenberg.GotenbergClientConfig
import no.nav.soknad.pdfutilities.gotenberg.GotenbergConvertToPdf
import no.nav.soknad.pdfutilities.gotenberg.ToPdfConverterTest
import org.junit.Test
import org.junit.jupiter.api.Disabled
import kotlin.test.assertEquals

class PdfMergerTest {

	private val docToPdfConverter: FileToPdfInterface = ToPdfConverterTest()
	// Require running gotenberg docker container locally: docker run --rm -p 3000:3000 gotenberg/gotenberg:8
	//private val docToPdfConverter: FileToPdfInterface = GotenbergConvertToPdf(GotenbergClientConfig("http://localhost:3000").getGotenbergClient())
	private val pdfMerger = PdfMerger(docToPdfConverter)

	private val antallSider = AntallSider()
	private val validerer = Validerer()

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

		//val erPdfa = validerer.isPDFa(mergedPdf)
		//TestCase.assertTrue(erPdfa)

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
//		val erPdfa2 = validerer.isPDFa(mergedPdf2)
//		TestCase.assertTrue(erPdfa2)

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
		// Skriver til fil for manuell verifisering av sammenslåtte PDFer.
		writeBytesToFile(mergedPdf, "delme-merged.pdf")

	}

}
