package no.nav.soknad.pdfutilities

import junit.framework.TestCase
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import no.nav.soknad.pdfutilities.azure.DocxToPdfConverterTest
import org.junit.Test
import kotlin.test.assertEquals

class PdfMergerTest {

	private val docToPdfConverter: DocxToPdfInterface = DocxToPdfConverterTest()
	private val konverterTilPdf = KonverterTilPdf(docToPdfConverter)

	private val soknadDto = DokumentSoknadDtoTestBuilder().build()
	private val pdfMerger = PdfMerger()
	private val antallSider = AntallSider()
	private val validerer = Validerer()

	@Test
	fun `sjekk at pdf filer blir merget`() {

		val pdfFiler = mutableListOf<ByteArray>()
		val antallFiler = 2
		for (i in 0 until antallFiler) {
			pdfFiler.add(konverterTilPdfOgReturner("/2MbJpg.jpg"))
		}

		val start = System.currentTimeMillis()
		val mergedPdf = pdfMerger.mergePdfer(pdfFiler)
		val ferdig = System.currentTimeMillis()
		println("Tid brukt for å merge ${pdfFiler.size} PDFer = ${ferdig - start}")

		val antallSider = antallSider.finnAntallSider(mergedPdf)
		assertEquals(antallFiler, antallSider)

		val erPdfa = validerer.isPDFa(mergedPdf)
		TestCase.assertTrue(erPdfa)

	}


	@Test
	fun `sjekk merging av mange pdf filer`() {
		val pdfFiler = mutableListOf<ByteArray>()
		val antallFiler = 10

		for (i in 0 until antallFiler) {
			pdfFiler.add(konverterTilPdfOgReturner("/2MbJpg.jpg"))
		}

		val start2 = System.currentTimeMillis()
		val mergedPdf2 = pdfMerger.mergePdfer(pdfFiler)
		val ferdig2 = System.currentTimeMillis()
		println("Tid brukt for å merge ${pdfFiler.size} PDFer = ${ferdig2 - start2}")

		assertEquals(pdfFiler.size, AntallSider().finnAntallSider(mergedPdf2))
		val erPdfa2 = validerer.isPDFa(mergedPdf2)
		TestCase.assertTrue(erPdfa2)

	}


	@Test
	fun `sjekk merging av flere pdfer der en inneholder mange sider`() {

		val pdfFiler = mutableListOf<ByteArray>()
		val antallFiler = 2
		for (i in 0 until antallFiler) {
			pdfFiler.add(konverterTilPdfOgReturner("/2MbJpg.jpg"))
		}
		val storPdf = Hjelpemetoder.getBytesFromFile("/storPdf.pdf")
		pdfFiler.add(storPdf)

		val start = System.currentTimeMillis()
		val mergedPdf = pdfMerger.mergePdfer(pdfFiler)
		val ferdig = System.currentTimeMillis()
		println("Tid brukt for å merge ${pdfFiler.size} PDFer der en av PDFene består av mange sider = ${ferdig - start}")

		assertEquals(antallFiler + (antallSider.finnAntallSider(storPdf) ?: 0), AntallSider().finnAntallSider(mergedPdf))

	}


	private fun konverterTilPdfOgReturner(filPath: String): ByteArray {
		val jpg = Hjelpemetoder.getBytesFromFile(filPath)

		val (pdf, antallSider) = konverterTilPdf.tilPdf(jpg, soknad = soknadDto)
		assertEquals(1, antallSider)

		val erPdfa = Validerer().isPDFa(pdf)
		TestCase.assertTrue(erPdfa)
		return pdf
	}

}
