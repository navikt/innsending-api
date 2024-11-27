package no.nav.soknad.pdfutilities

interface FileToPdfInterface {
	fun toPdf(fileName: String, fileContent: ByteArray): ByteArray

	fun imageToPdf(fileName: String, fileContent: ByteArray): ByteArray

	fun mergePdfs(fileName: String, docs: List<ByteArray>): ByteArray
}
