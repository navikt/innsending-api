package no.nav.soknad.arkivering.soknadsMottaker.dto

import java.time.LocalDateTime


data class SoknadInnsendtDto1(val innsendingsId: String, val ettersendelse: Boolean, val personId: String, val tema: String,
														 val innsendtDato: LocalDateTime, val innsendteDokumenter: Array<InnsendtDokumentDto1>)

data class InnsendtDokumentDto1(val skjemaNummer: String, val erHovedSkjema: Boolean?,
															 val tittel: String?, val varianter: Array<InnsendtVariantDto1>)

data class InnsendtVariantDto1(val uuid: String, val mimeType: String?, val filNavn: String?,
															val filStorrelse: String?, val variantformat: String?, val filtype: String?)
