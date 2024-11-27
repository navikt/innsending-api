package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.exceptions.BackendErrorException
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm
import org.slf4j.LoggerFactory

class CheckAndFormatPdf {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun flatUtPdf(pdfMerger: PdfMerger, fil: ByteArray, antallSider: Int): ByteArray {
		logger.info("Antall sider i PDF: {}", antallSider)

		// Konvertere fra PDF til bilde og tilbake til PDF
		// Max størrelse på vedlegg er 50mb og for å ikke overskride dette så konverterer vi ikke PDF'er på over 50 sider
		// (PDF'en blir veldig mye større med png av hver side)
		if (harSkrivbareFelt(fil) && antallSider <= 50) {
			val start = System.currentTimeMillis()

			val images = KonverterTilPng().konverterTilPng(fil)
			val pdfList = mutableListOf<ByteArray>()
			for (element in images) pdfList.add(ConvertImageToPdf().pdfFromImage(element).first)

			val end = System.currentTimeMillis()
			logger.info("Tid brukt for å konvertere PDF til bilde og tilbake til PDF = {}", end - start)

			return pdfMerger.mergePdfer(pdfList)
		} else if (antallSider > 50) {
			logger.info("Antall sider = $antallSider er over max grense (50) for utflating av PDF")
		}

		return fil
	}

	fun harSkrivbareFelt(input: ByteArray?): Boolean {
		try {
			Loader.loadPDF(input).use { document ->
				val acroForm = getAcroForm(document)
				return acroForm != null
			}
		} catch (e: Exception) {
			throw BackendErrorException("Feil ved mottak av opplastet fil", e)
		}
	}

	private fun getAcroForm(pdfDocument: PDDocument): PDAcroForm? {
		return pdfDocument.documentCatalog.acroForm
	}

}
