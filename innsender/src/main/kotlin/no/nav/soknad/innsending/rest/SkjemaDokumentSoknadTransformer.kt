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
		kanLasteOppAnnet = input.vedleggsListe?.any { it.vedleggsnr == "N6" && !it.pakrevd })


	/**
	 * Behandling av vedlegg av type Annet (skjemanr=N6):
	 * Hvis FyllUt Ikke har lagt ved et vedlegg med skjemanr=N6, så skal IKKE knapp for søker til å legge til vedlegg av type Annet vedlegg være tilgjengelig.
	 * Hvis FyllUt har lagt til et vedlegg med skjemanr=N6 og tittel=Annet og pakrevd=false, så skal IKKE vedleggselement legges til, men knapp for å legge til Annet vedlegg skal være tilgjengelig.
	 * Hvis FyllUt har lagt til et vedlegg med skjemanr=N6 og tittel!=Annet eller pakrevd=true, så skal vedleggselementet legges til. Merk det skal ikke være mulig å slette vedlegg dersom pakrevd=true.
	 * På DokumentSoknadDto settes kanLasteOppAnnet=true dersom FyllUt legger ved vedlegg av type Annet. => Frontend legger på knapp for å legge til Annet vedlegg.
	 * FyllUt spesifiserer pr vedlegg om pakrevd. Dersom pakrevd=false OG skjemanr=N6, så skal søker kunne slette vedlegget.
	 */
	private fun lagVedleggsListe(skjemaDto: SkjemaDto): List<VedleggDto> {
		val hoveddok = konverterTilVedleggDto(skjemaDto.hoveddokument, erHoveddokument = true, erVariant = false)
		val variant = konverterTilVedleggDto(skjemaDto.hoveddokumentVariant, erHoveddokument = true, erVariant = true)
		val vedleggListe: List<VedleggDto>? =
			skjemaDto.vedleggsListe
				?.filter{ it.vedleggsnr != "N6" || it.label != "Annet" || it.pakrevd }
				?.map { konverterTilVedleggDto(it, erHoveddokument = false, erVariant = false) }

		return listOf(hoveddok, variant) + if (vedleggListe.isNullOrEmpty()) emptyList() else vedleggListe
	}

	private fun konverterTilVedleggDto(skjemaDokumentDto: SkjemaDokumentDto, erHoveddokument: Boolean, erVariant: Boolean): VedleggDto =
		VedleggDto(skjemaDokumentDto.tittel, skjemaDokumentDto.label,erHoveddokument, erVariant,
			skjemaDokumentDto.mimetype?.equals(Mimetype.applicationSlashPdf) ?: false, skjemaDokumentDto.pakrevd,
			if (skjemaDokumentDto.document != null) OpplastingsStatusDto.lastetOpp else OpplastingsStatusDto.ikkeValgt,
			LocalDateTime.now().atOffset(ZoneOffset.UTC), null, skjemaDokumentDto.vedleggsnr,  skjemaDokumentDto.beskrivelse,
			null, skjemaDokumentDto.mimetype, skjemaDokumentDto.document,null )
}
