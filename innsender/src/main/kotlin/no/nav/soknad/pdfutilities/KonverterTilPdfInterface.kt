package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.model.DokumentSoknadDto

interface KonverterTilPdfInterface {
	fun tilPdf(
		fil: ByteArray,
		innsendingId: String,
		filtype: String,
		tittel: String,
		spraak: String?,
	): Pair<ByteArray, Int>

	fun harSkrivbareFelt(input: ByteArray?): Boolean

	fun flatUtPdf(fil: ByteArray, antallSider: Int): ByteArray
}
