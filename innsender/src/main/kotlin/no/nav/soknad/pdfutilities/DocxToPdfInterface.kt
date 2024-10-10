package no.nav.soknad.pdfutilities

interface DocxToPdfInterface {
	fun convertDocxToPdf(fileContent: ByteArray): ByteArray
}
