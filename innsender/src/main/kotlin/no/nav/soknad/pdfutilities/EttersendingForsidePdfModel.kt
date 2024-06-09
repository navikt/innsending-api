package no.nav.soknad.pdfutilities

data class EttersendingForsidePdfModel(
	val ettersendingHeader: String,
	val ettersendelseTittel: String,
	val tittel: String,
	val personInfo: String,
	val oppsummering: String
)
