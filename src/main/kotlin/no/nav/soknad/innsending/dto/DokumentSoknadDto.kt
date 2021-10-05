package no.nav.soknad.innsending.dto

import no.nav.soknad.innsending.repository.SoknadsStatus
import java.time.LocalDateTime

data class DokumentSoknadDto(val id: Long?, val behandlingsId: String?, val ettersendingsId: String?, val brukerId: String, val skjemanr: String, val tittel:String , val tema: String, val spraak: String?
, val status: SoknadsStatus
, val opprettetDato: LocalDateTime, val endretDato: LocalDateTime?, val innsendtDato: LocalDateTime?
, val vedleggsListe: List<VedleggDto>
)
