package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.model.DokumentSoknadDto

interface KonverterTilPdfInterface {
	fun tilPdf(
		fil: ByteArray,
		soknad: DokumentSoknadDto,
		sammensattNavn: String? = null,
		vedleggsTittel: String? = "Annet"
	): Pair<ByteArray, Int>

	fun harSkrivbareFelt(input: ByteArray?): Boolean

	fun flatUtPdf(fil: ByteArray, antallSider: Int): ByteArray
}