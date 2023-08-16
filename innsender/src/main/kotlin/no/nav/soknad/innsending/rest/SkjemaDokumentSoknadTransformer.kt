package no.nav.soknad.innsending.rest

import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.finnSpraakFraInput
import no.nav.soknad.innsending.util.mapping.mapTilOffsetDateTime
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class SkjemaDokumentSoknadTransformer {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun konverterTilDokumentSoknadDto(input: SkjemaDto, brukerId: String): DokumentSoknadDto = DokumentSoknadDto(
		brukerId = brukerId,
		skjemanr = input.skjemanr,
		tittel = input.tittel,
		tema = input.tema,
		status = SoknadsStatusDto.opprettet,
		opprettetDato = mapTilOffsetDateTime(LocalDateTime.now())!!,
		endretDato = mapTilOffsetDateTime(LocalDateTime.now()),
		vedleggsListe = lagVedleggsListe(input),
		id = null,
		innsendingsId = null,
		ettersendingsId = null,
		spraak = finnSpraakFraInput(input.spraak),
		innsendtDato = null,
		visningsSteg = 0,
		visningsType = VisningsType.fyllUt,
		kanLasteOppAnnet = input.kanLasteOppAnnet
			?: input.vedleggsListe?.any { it.propertyNavn != null && it.propertyNavn == "annenDokumentasjon" },
		forsteInnsendingsDato = null,
		fristForEttersendelse = input.fristForEttersendelse ?: Constants.DEFAULT_FRIST_FOR_ETTERSENDELSE,
		arkiveringsStatus = ArkiveringsStatusDto.ikkeSatt,
		soknadstype = SoknadType.utkast,
	)

//	kanLasteOppAnnet = input.vedleggsListe?.any { it.property == "annenDokumentasjon" : it.vedleggsnr == "N6" && it.label == "Annen dokumentasjon" })

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
		val vedleggListe: List<VedleggDto> =
			(skjemaDto.vedleggsListe ?: emptyList())
				.filter { !(it.propertyNavn != null && it.propertyNavn == "annenDokumentasjon") }
				.map { konverterTilVedleggDto(it, erHoveddokument = false, erVariant = false) }

		logger.debug(
			"Søknad med skjemanr ${hoveddok.vedleggsnr} har vedleggene ${
				vedleggListe.map { it.vedleggsnr }.joinToString(", ")
			}"
		)
		return listOf(hoveddok, variant) + vedleggListe
	}

	private fun konverterTilVedleggDto(
		skjemaDokumentDto: SkjemaDokumentDto,
		erHoveddokument: Boolean,
		erVariant: Boolean
	): VedleggDto =
		VedleggDto(
			tittel = skjemaDokumentDto.tittel,
			label = skjemaDokumentDto.label,
			erHoveddokument = erHoveddokument,
			erVariant = erVariant,
			erPdfa = skjemaDokumentDto.mimetype?.equals(Mimetype.applicationSlashPdf) ?: false,
			erPakrevd = skjemaDokumentDto.pakrevd,
			opplastingsStatus = if (skjemaDokumentDto.document != null) OpplastingsStatusDto.lastetOpp else OpplastingsStatusDto.ikkeValgt,
			opprettetdato = mapTilOffsetDateTime(LocalDateTime.now())!!,
			id = null,
			vedleggsnr = skjemaDokumentDto.vedleggsnr,
			beskrivelse = skjemaDokumentDto.beskrivelse,
			uuid = null,
			mimetype = skjemaDokumentDto.mimetype,
			document = skjemaDokumentDto.document,
			skjemaurl = null,
			innsendtdato = null,
			formioId = skjemaDokumentDto.formioId,
		)
}
