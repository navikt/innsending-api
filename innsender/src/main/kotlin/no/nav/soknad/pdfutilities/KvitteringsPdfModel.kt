package no.nav.soknad.pdfutilities

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
)
