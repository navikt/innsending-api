package no.nav.soknad.innsending.dto

data class SkjemaDto(
	val brukerId: String,
	val skjemanr: String,
	val tittel: String,
	val tema: String,
	val spraak: String,
	val vedleggsListe: List<SkjemaDokumentDto>
)
