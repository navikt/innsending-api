package no.nav.soknad.innsending.dto

import no.nav.soknad.innsending.repository.OpplastingsStatus
import no.nav.soknad.innsending.repository.SoknadsStatus
import no.nav.soknad.innsending.util.finnSpraakFraInput
import org.apache.xmpbox.type.MIMEType
import java.time.LocalDateTime

class SkjemaDokumentSoknadTransformer {

	fun konverterTilDokumentSoknadDto(input: SkjemaDto): DokumentSoknadDto = DokumentSoknadDto(null, null, null, input.brukerId,
		input.skjemanr, input.tittel, input.tema, finnSpraakFraInput(input.spraak), SoknadsStatus.Opprettet, LocalDateTime.now(), LocalDateTime.now(), null, lagVedleggsListe(input) )


	private fun lagVedleggsListe(skjemaDto: SkjemaDto): List<VedleggDto> {
		val hoveddok = konverterTilVedleggDto(skjemaDto.hoveddokument, true, false)
		val variant = konverterTilVedleggDto(skjemaDto.hoveddokumentVariant, true, false)
		val vedleggListe: List<VedleggDto>? = skjemaDto.vedleggsListe?.map { konverterTilVedleggDto(it, false, false) }
			?.toList()

		return listOf(hoveddok, variant) + if (vedleggListe.isNullOrEmpty()) emptyList() else vedleggListe
	}

	private fun konverterTilVedleggDto(skjemaDokumentDto: SkjemaDokumentDto, erHoveddokument: Boolean, erVariant: Boolean): VedleggDto =
		VedleggDto(null, skjemaDokumentDto.vedleggsnr, skjemaDokumentDto.tittel, null, skjemaDokumentDto.mimetype, skjemaDokumentDto.document,
		erHoveddokument, erVariant, skjemaDokumentDto.mimetype?.contains("application/pdf") ?: false, skjemaDokumentDto.pakrevd, null,if (skjemaDokumentDto.document != null) OpplastingsStatus.LASTET_OPP else OpplastingsStatus.IKKE_VALGT, LocalDateTime.now() )


}
