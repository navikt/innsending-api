package no.nav.soknad.pdfutilities.models

import no.nav.soknad.pdfutilities.VedleggsKategori
import no.nav.soknad.pdfutilities.utils.PdfUtils

data class KvitteringsPdfModel(
	val sprak: String = "nb-NO",
	val beskrivelse: String,
	val kvitteringHeader: String,
	val ettersendelseTittel: String?,
	val side: String,
	val av: String,
	val tittel: String,
	val personInfo: String,
	val innsendtTidspunkt: String,
	val vedleggsListe: List<VedleggsKategori>,
) {
	fun vasket(): KvitteringsPdfModel {
		return KvitteringsPdfModel(
			sprak = this.sprak,
			beskrivelse = this.beskrivelse,
			kvitteringHeader = PdfUtils.fjernSpesielleKarakterer(this.kvitteringHeader) ?: "",
			ettersendelseTittel = PdfUtils.fjernSpesielleKarakterer(this.ettersendelseTittel),
			side = this.side,
			av = this.av,
			tittel = PdfUtils.fjernSpesielleKarakterer(this.tittel) ?: "",
			personInfo = this.personInfo,
			innsendtTidspunkt = this.innsendtTidspunkt,
			vedleggsListe = this.vedleggsListe
		)
	}
}
