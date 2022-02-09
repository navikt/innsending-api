package no.nav.soknad.innsending.consumerapis.pdl.dto

import java.time.LocalDateTime

data class FolkeregisterMetadataDto(
	val ajourholdstidspunkt: LocalDateTime?,
	val kilde: String?
)
