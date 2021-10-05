package no.nav.soknad.innsending.dto

import java.time.LocalDateTime

data class AktivSakDto(val behandlingsId: String?, val skjemanr: String, val tittel: String, val tema: String, val innsendtDato: LocalDateTime, val ettersending: Boolean
                        , val innsendtVedleggDtos: List<InnsendtVedleggDto>)