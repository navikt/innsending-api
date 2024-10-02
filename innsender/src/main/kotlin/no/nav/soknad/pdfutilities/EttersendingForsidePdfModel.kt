package no.nav.soknad.pdfutilities

data class EttersendingForsidePdfModel(
	val sprak: String = "nb-NO",
	val beskrivelse: String,
	val navnLabel: String,
	val fnrLabel: String,
	val ettersendingHeader: String,
	val ettersendelseTittel: String,
	val side: String,
	val av: String,
	val tittel: String,
	val personInfo: String,
	val personIdent: String,
	val dato: String,
	val innsendtTidspunkt: String
)
