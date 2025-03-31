package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.exceptions.BackendErrorException
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CheckAndFormatPdf(
	private val pdfConverter: FileToPdfInterface
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun flatUtPdf(pdfMerger: PdfMerger, title: String?, subject: String?, fil: ByteArray, antallSider: Int): ByteArray {
		logger.info("Antall sider i PDF: {}", antallSider)

		// Konvertere fra PDF til bilde og tilbake til PDF
		// Max størrelse på vedlegg er 50mb og for å ikke overskride dette så konverterer vi ikke PDF'er på over 50 sider
		// (PDF'en blir veldig mye større med png av hver side)
		if (antallSider <= 50 && harSkrivbareFelt(fil) ) {
			val start = System.currentTimeMillis()
			val flatetFil = flattenPdf(pdfMerger, title = title, subject = subject, doc = fil)
			val end = System.currentTimeMillis()
			logger.info("Tid brukt for å konvertere PDF til bilde og tilbake til PDF = {}", end - start)
			return flatetFil
		} else if (antallSider > 50) {
			logger.info("Antall sider = $antallSider er over max grense (50) for utflating av PDF")
		}

		return fil
	}


	fun flattenPdf(pdfMerger: PdfMerger, title: String?, subject: String?, doc: ByteArray): ByteArray {
		return try {
			flattenWithGotenberg(title, subject, doc)
		} catch (e: Exception) {
			try {
				logger.warn("Flating av fil v.hj.a. Gotenberg feilet, forsøker med PDFBox")
				flattenWithPDFBox(pdfMerger, doc, title, subject)
			} catch (ex: Exception) {
				logger.error("Flating av fil feilet")
				throw ex
			}
		}

	}

	fun flattenWithGotenberg(title: String?, subject: String?, doc: ByteArray): ByteArray {
		val metadata = pdfConverter.buildMetadata(title, subject)
		return pdfConverter.flattenPdfs(UUID.randomUUID().toString(), metadata, listOf(doc))
	}

	fun flattenWithPDFBox(pdfMerger: PdfMerger, fil: ByteArray, title: String?, subject: String?) : ByteArray {
		val images = KonverterTilPng().konverterTilPng(fil)
		val pdfList = mutableListOf<ByteArray>()
		for (element in images) pdfList.add(ConvertImageToPdf().pdfFromImage(element).first)
		return pdfMerger.mergePdfer(title, subject, pdfList)
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
