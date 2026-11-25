package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.utils.Hjelpemetoder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled

class PdfMergerTest {

	private val pdfMerger: PdfMergerInterface = GotenbergTestConverters.pdfMerger

	private val antallSider = AntallSider()

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
	}

	@Test
	fun `Sjekk merging av førsteside med søknad for UU støtte`() {

		val pdfFiler = mutableListOf<ByteArray>()
		val antallFiler = 1
		val fil = Hjelpemetoder.getBytesFromFile("/__files/forsteside-nav.pdf")
		for (i in 0 until antallFiler) {
			pdfFiler.add(fil)
		}
		val soknad = Hjelpemetoder.getBytesFromFile("/__files/NAV 12-15.01.pdf")
		pdfFiler.add(soknad)

		val start = System.currentTimeMillis()
		val mergedPdf = pdfMerger.mergeWithPDFBox(pdfFiler)
		val mergedPdfFixed = pdfMerger.setPdfMetadata(file = mergedPdf, title = "Søknad om barnetillegg for deg som har uføretrygd", language = "no-NB")
		val ferdig = System.currentTimeMillis()
		println("Tid brukt for å merge ${pdfFiler.size} PDFer der en av PDFene består av mange sider = ${ferdig - start}")

		assertEquals(antallFiler + (antallSider.finnAntallSider(soknad) ?: 0), AntallSider().finnAntallSider(mergedPdfFixed))
	}

	@Test
	fun `Sjekk merging av filer med UU støtte`() {

		val pdfFiler = mutableListOf<ByteArray>()
		val antallFiler = 1
		val fil = Hjelpemetoder.getBytesFromFile("/__files/ny-forsteside.pdf")
		for (i in 0 until antallFiler) {
			pdfFiler.add(fil)
		}
		val soknad = Hjelpemetoder.getBytesFromFile("/__files/NAV 12-15.01.pdf")
		pdfFiler.add(soknad)

		val start = System.currentTimeMillis()
		val mergedPdf = pdfMerger.mergeWithPDFBox(pdfFiler)
		val mergedPdfFixed = pdfMerger.setPdfMetadata(file = mergedPdf, title = "Søknad om barnetillegg for deg som har uføretrygd", language = "no-NB")
		val ferdig = System.currentTimeMillis()
		println("Tid brukt for å merge ${pdfFiler.size} PDFer der en av PDFene består av mange sider = ${ferdig - start}")

		assertEquals(antallFiler + (antallSider.finnAntallSider(soknad) ?: 0), AntallSider().finnAntallSider(mergedPdfFixed))
	}

	@Test
	@Disabled("StorPdf.pdf er 47,9MB. Max vedleggsstørrelse er 150MB. Opplasting av 3 slike er relevant, men tar mer enn 1 minutt. Disabler testen, men lar den liggee så den kan kjøres ved behov.")
	fun `sjekk merging av flere store pdfer`() {
		val pdfFiler = mutableListOf<ByteArray>()
		val antallFiler = 3
		for (i in 0 until antallFiler) {
			pdfFiler.add(Hjelpemetoder.getBytesFromFile("/storPdf.pdf"))
		}

		val start = System.currentTimeMillis()
		val mergedPdf = pdfMerger.mergePdfer(pdfFiler)
		val ferdig = System.currentTimeMillis()
		println("Tid brukt for å merge ${pdfFiler.size} PDFer der en av PDFene består av mange sider = ${ferdig - start}")
		assertTrue(mergedPdf.size > 0)
	}

}
