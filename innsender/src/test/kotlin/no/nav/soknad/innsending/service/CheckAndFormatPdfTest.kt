package no.nav.soknad.innsending.service

import junit.framework.TestCase.assertTrue
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import no.nav.soknad.pdfutilities.*
import no.nav.soknad.testutils.ToPdfConverterTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CheckAndFormatPdfTest {


	private val docToPdfConverter: FileToPdfInterface = ToPdfConverterTest()
	private val pdfMerger = PdfMerger(docToPdfConverter)
	private val checkAndFormatPdf = CheckAndFormatPdf(docToPdfConverter)

	private val konverterTilPdf = KonverterTilPdf(docToPdfConverter, pdfMerger, checkAndFormatPdf)

	private val soknadDto = DokumentSoknadDtoTestBuilder().build()


	@Test
	fun verifiserFlatingAvPdf() {
		val skrivbarPdf = Hjelpemetoder.getBytesFromFile("/NAV 54-editert.pdf")
		assertTrue(konverterTilPdf.harSkrivbareFelt(skrivbarPdf))

		val antallSiderSkrivbarPdf = AntallSider().finnAntallSider(skrivbarPdf)
		val start = System.currentTimeMillis()
		val flatetPdf = konverterTilPdf.flatUtPdf(skrivbarPdf, antallSiderSkrivbarPdf ?: 0)
		val ferdig = System.currentTimeMillis()
		println("Tid til flate ut PDF = ${ferdig - start}")

		//writeBytesToFile(flatetPdf, "./delme.pdf")
		assertEquals(false, konverterTilPdf.harSkrivbareFelt(flatetPdf))

		val antallSiderFlatetPdf = AntallSider().finnAntallSider(flatetPdf)
		assertEquals(antallSiderSkrivbarPdf, antallSiderFlatetPdf)

		val erPdfa = Validerer().isPDFa(flatetPdf)
		assertTrue(erPdfa)

	}

	@Test
	fun `Skal ikke flate ut PDF'er på over 50 sider`() {
		val skrivbarPdf = Hjelpemetoder.getBytesFromFile("/pdfs/acroform-fields-tom-array.pdf")
		assertTrue(konverterTilPdf.harSkrivbareFelt(skrivbarPdf))

		val antallSiderSkrivbarPdf = AntallSider().finnAntallSider(skrivbarPdf) ?: 0
		val flatetPdf = konverterTilPdf.flatUtPdf(skrivbarPdf, antallSiderSkrivbarPdf)
		assertTrue(konverterTilPdf.harSkrivbareFelt(flatetPdf)) // Skal fortsatt ha skrivbare felt

		assertEquals(antallSiderSkrivbarPdf, AntallSider().finnAntallSider(flatetPdf))
		assertEquals(skrivbarPdf, flatetPdf)
	}

}
