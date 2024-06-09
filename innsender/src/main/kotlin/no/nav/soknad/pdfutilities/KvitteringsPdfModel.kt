package no.nav.soknad.pdfutilities

data class KvitteringsPdfModel(
	val kvitteringHeader: String,
	val ettersendelseTittel: String?,
	val tittel: String,
	val personInfo: String,
	val antallInnsendt: String,
	val data: List<VedleggsKategori>,
)
