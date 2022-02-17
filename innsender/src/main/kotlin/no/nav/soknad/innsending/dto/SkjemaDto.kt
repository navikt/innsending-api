package no.nav.soknad.innsending.dto

data class SkjemaDto(
	val brukerId: String,
	val skjemanr: String,
	val tittel: String,
	val tema: String,
	val spraak: String,
	val hoveddokument: SkjemaDokumentDto,
	val hoveddokumentVariant: SkjemaDokumentDto,
	val vedleggsListe: List<SkjemaDokumentDto>?
)
