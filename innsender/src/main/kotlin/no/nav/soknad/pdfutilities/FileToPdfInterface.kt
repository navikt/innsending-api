package no.nav.soknad.pdfutilities

interface FileToPdfInterface {
	fun toPdf(fileName: String, fileContent: ByteArray): ByteArray

	fun imageToPdf(fileName: String, fileContent: ByteArray): ByteArray

	fun mergePdfs(fileName: String, docs: List<ByteArray>): ByteArray

	// Flater ut skjemafelt og andre annotasjoner i én PDF. trace sendes som Gotenberg-Trace for å kunne korrelere logger.
	fun flattenPdf(fileContent: ByteArray, trace: String): ByteArray
}
