package no.nav.soknad.arkivering.soknadsMottaker.dto

import java.time.LocalDateTime


data class SoknadInnsendtDto(val innsendingsId: String, val ettersendelse: Boolean, val personId: String, val tema: String,
														 val innsendtDato: LocalDateTime, val innsendteDokumenter: Array<InnsendtDokumentDto>)

data class InnsendtDokumentDto(val skjemaNummer: String, val erHovedSkjema: Boolean?,
															 val tittel: String?, val varianter: Array<InnsendtVariantDto>)

data class InnsendtVariantDto(val uuid: String, val mimeType: String?, val filNavn: String?,
															val filStorrelse: String?, val variantformat: String?, val filtype: String?)
