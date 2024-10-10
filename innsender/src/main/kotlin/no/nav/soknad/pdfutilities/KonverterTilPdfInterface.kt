package no.nav.soknad.pdfutilities

interface KonverterTilPdfInterface {
	fun tilPdf(fil: ByteArray): Pair<ByteArray, Int>

	fun harSkrivbareFelt(input: ByteArray?): Boolean

	fun flatUtPdf(fil: ByteArray, antallSider: Int): ByteArray
}
