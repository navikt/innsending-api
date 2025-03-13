package no.nav.soknad.pdfutilities

interface FileToPdfInterface {
	fun toPdf(fileName: String, fileContent: ByteArray): ByteArray

	fun imageToPdf(fileName: String, fileContent: ByteArray): ByteArray

	fun mergePdfs(fileName: String, docs: List<ByteArray>): ByteArray

	fun flattenPdfs(fileName: String, metadata: String, docs: List<ByteArray>): ByteArray

	fun buildMetadata(title: String? = null, subject: String? = null, author: String? = null, keywords: List<String>? = null): String
}
