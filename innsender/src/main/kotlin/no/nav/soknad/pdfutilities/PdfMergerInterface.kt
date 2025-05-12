package no.nav.soknad.pdfutilities

interface PdfMergerInterface {
	fun mergePdfer(docs: List<ByteArray>): ByteArray

	fun mergeWithPDFBox(docs: List<ByteArray>): ByteArray

	fun setPdfMetadata(file: ByteArray, title: String, language: String?): ByteArray
}
