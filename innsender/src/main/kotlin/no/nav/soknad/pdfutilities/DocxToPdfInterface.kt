package no.nav.soknad.pdfutilities

interface DocxToPdfInterface {
	fun toPdf(fileName: String, fileContent: ByteArray): ByteArray
}
