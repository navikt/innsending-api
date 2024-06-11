package no.nav.soknad.pdfutilities

data class EttersendingForsidePdfModel(
	val ettersendingHeader: String,
	val ettersendelseTittel: String,
	val side: String,
	val av: String,
	val tittel: String,
	val personInfo: String,
	val oppsummering: String
)
