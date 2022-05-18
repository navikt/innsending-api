package no.nav.soknad.innsending.consumerapis.saf.dto

data class ArkiverteSaker(
	val eksternReferanseId: String?,
	val tittel: String,
	val tema: String,
	val datoMottatt: String?,
	val dokumenter: List<Dokument>
)

data class Dokument (
	val brevkode: String?,
	val tittel: String,
	val k_tilkn_jp_som: String // HOVEDDOKUMENT, VEDLEGG
)
