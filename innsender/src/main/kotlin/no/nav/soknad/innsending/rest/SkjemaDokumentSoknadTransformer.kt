package no.nav.soknad.innsending.rest

import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.util.finnSpraakFraInput
import java.time.LocalDateTime
import java.time.ZoneOffset

class SkjemaDokumentSoknadTransformer {

	fun konverterTilDokumentSoknadDto(input: SkjemaDto, brukerId: String): DokumentSoknadDto = DokumentSoknadDto(brukerId,
		input.skjemanr, input.tittel, input.tema, SoknadsStatusDto.opprettet, LocalDateTime.now().atOffset(ZoneOffset.UTC),
		lagVedleggsListe(input), null, null, null, finnSpraakFraInput(input.spraak),
		LocalDateTime.now().atOffset(ZoneOffset.UTC), null, 0, VisningsType.fyllUt,
		input.vedleggsListe?.any { it.vedleggsnr == "N6"})


	private fun lagVedleggsListe(skjemaDto: SkjemaDto): List<VedleggDto> {
		val hoveddok = konverterTilVedleggDto(skjemaDto.hoveddokument, true, false)
		val variant = konverterTilVedleggDto(skjemaDto.hoveddokumentVariant, true, true)
		val vedleggListe: List<VedleggDto>? = skjemaDto.vedleggsListe?.map { konverterTilVedleggDto(it, false, false) }
			?.toList()

		return listOf(hoveddok, variant) + if (vedleggListe.isNullOrEmpty()) emptyList() else vedleggListe
	}

	private fun konverterTilVedleggDto(skjemaDokumentDto: SkjemaDokumentDto, erHoveddokument: Boolean, erVariant: Boolean): VedleggDto =
		VedleggDto(skjemaDokumentDto.tittel, skjemaDokumentDto.label,erHoveddokument, erVariant,
			skjemaDokumentDto.mimetype?.equals(Mimetype.applicationSlashPdf) ?: false, skjemaDokumentDto.pakrevd,
			if (skjemaDokumentDto.document != null) OpplastingsStatusDto.lastetOpp else OpplastingsStatusDto.ikkeValgt,
			LocalDateTime.now().atOffset(ZoneOffset.UTC), null, skjemaDokumentDto.vedleggsnr,  skjemaDokumentDto.beskrivelse,
			null, skjemaDokumentDto.mimetype, skjemaDokumentDto.document,null )


}
