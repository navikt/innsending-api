package no.nav.soknad.innsending.service

import no.nav.soknad.arkivering.soknadsmottaker.model.DocumentData
import no.nav.soknad.arkivering.soknadsmottaker.model.Soknad
import no.nav.soknad.arkivering.soknadsmottaker.model.Varianter
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.*
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.models.hoveddokument
import no.nav.soknad.innsending.util.models.hoveddokumentVariant
import no.nav.soknad.innsending.util.models.vedleggsListeUtenHoveddokument
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*
import kotlin.math.absoluteValue

const val ukjentEttersendingsId = "-1" // sette lik innsendingsid istedenfor?

fun erEttersending(dokumentSoknad: DokumentSoknadDto): Boolean =
	(dokumentSoknad.ettersendingsId != null) || (dokumentSoknad.visningsType == VisningsType.ettersending)

fun lagVedleggDtoMedOpplastetFil(filDto: FilDto?, vedleggDto: VedleggDto) =
	VedleggDto(
		tittel = vedleggDto.tittel,
		label = vedleggDto.label,
		erHoveddokument = vedleggDto.erHoveddokument,
		erVariant = vedleggDto.erVariant,
		erPdfa = vedleggDto.erPdfa,
		erPakrevd = vedleggDto.erPakrevd,
		opplastingsStatus = avledOpplastingsstatusVedInnsending(filDto, vedleggDto),
		opprettetdato = filDto?.opprettetdato ?: vedleggDto.opprettetdato,
		id = vedleggDto.id!!,
		vedleggsnr = vedleggDto.vedleggsnr,
		beskrivelse = vedleggDto.beskrivelse,
		uuid = vedleggDto.uuid,
		mimetype = filDto?.mimetype ?: vedleggDto.mimetype,
		document = null,
		skjemaurl = vedleggDto.skjemaurl,
		innsendtdato = OffsetDateTime.now()
	)


private fun avledOpplastingsstatusVedInnsending(filDto: FilDto?, vedleggDto: VedleggDto): OpplastingsStatusDto {
	// Dersom det er lastet opp en eller flere filer på vedlegget så skal filDto != null og størrelsen være satt
	if ((filDto != null) && (filDto.storrelse!! > 0)
		&& ((vedleggDto.opplastingsStatus == OpplastingsStatusDto.ikkeValgt) || (vedleggDto.opplastingsStatus == OpplastingsStatusDto.lastetOpp))
	) {
		return OpplastingsStatusDto.lastetOpp
	}
	return when (vedleggDto.opplastingsStatus) {
		OpplastingsStatusDto.ikkeValgt -> if (vedleggDto.erPakrevd) OpplastingsStatusDto.sendSenere else OpplastingsStatusDto.sendesIkke
		OpplastingsStatusDto.sendesAvAndre,
		OpplastingsStatusDto.sendSenere,
		OpplastingsStatusDto.innsendt -> vedleggDto.opplastingsStatus

		else -> if (vedleggDto.erPakrevd) OpplastingsStatusDto.sendSenere else OpplastingsStatusDto.sendesIkke
	}
}

fun lagFilDto(filDbData: FilDbData, medFil: Boolean = true) = FilDto(
	filDbData.vedleggsid, filDbData.id,
	filDbData.filnavn, mapTilMimetype(filDbData.mimetype), filDbData.storrelse,
	if (medFil) filDbData.data else null, mapTilOffsetDateTime(filDbData.opprettetdato)
)

fun lagVedleggDto(vedleggDbData: VedleggDbData, document: ByteArray? = null) =
	VedleggDto(
		tittel = vedleggDbData.tittel,
		label = vedleggDbData.label ?: "",
		erHoveddokument = vedleggDbData.erhoveddokument,
		erVariant = vedleggDbData.ervariant,
		erPdfa = vedleggDbData.erpdfa,
		erPakrevd = vedleggDbData.erpakrevd,
		opplastingsStatus = mapTilOpplastingsStatusDto(vedleggDbData.status),
		opprettetdato = mapTilOffsetDateTime(vedleggDbData.opprettetdato)!!,
		id = vedleggDbData.id!!,
		vedleggsnr = vedleggDbData.vedleggsnr,
		beskrivelse = vedleggDbData.beskrivelse,
		uuid = vedleggDbData.uuid,
		mimetype = mapTilMimetype(vedleggDbData.mimetype),
		document = document,
		skjemaurl = vedleggDbData.vedleggsurl,
		innsendtdato = mapTilOffsetDateTime(vedleggDbData.innsendtdato),
		formioId = vedleggDbData.formioid
	)

fun lagDokumentSoknadDto(
	soknadDbData: SoknadDbData,
	vedleggDbDataListe: List<VedleggDbData>,
	erSystemGenerert: Boolean = false
) =
	DokumentSoknadDto(
		brukerId = soknadDbData.brukerid,
		skjemanr = soknadDbData.skjemanr,
		tittel = soknadDbData.tittel,
		tema = soknadDbData.tema,
		status = mapTilSoknadsStatusDto(soknadDbData.status) ?: SoknadsStatusDto.opprettet,
		vedleggsListe = vedleggDbDataListe.map { lagVedleggDto(it) },
		id = soknadDbData.id!!,
		innsendingsId = soknadDbData.innsendingsid,
		ettersendingsId = soknadDbData.ettersendingsid,
		spraak = soknadDbData.spraak,
		opprettetDato = mapTilOffsetDateTime(soknadDbData.opprettetdato)!!,
		endretDato = mapTilOffsetDateTime(soknadDbData.endretdato),
		innsendtDato = mapTilOffsetDateTime(soknadDbData.innsendtdato),
		visningsSteg = soknadDbData.visningssteg ?: 0,
		visningsType = soknadDbData.visningstype
			?: if (soknadDbData.ettersendingsid != null) VisningsType.ettersending else VisningsType.dokumentinnsending,
		kanLasteOppAnnet = soknadDbData.kanlasteoppannet ?: true,
		innsendingsFristDato = beregnInnsendingsFrist(soknadDbData),
		forsteInnsendingsDato = mapTilOffsetDateTime(soknadDbData.forsteinnsendingsdato),
		fristForEttersendelse = soknadDbData.ettersendingsfrist ?: Constants.DEFAULT_FRIST_FOR_ETTERSENDELSE,
		arkiveringsStatus = mapTilArkiveringsStatusDto(soknadDbData.arkiveringsstatus),
		erSystemGenerert = erSystemGenerert
	)

private fun beregnInnsendingsFrist(soknadDbData: SoknadDbData): OffsetDateTime {
	if (soknadDbData.ettersendingsid == null) {
		return mapTilOffsetDateTime(soknadDbData.opprettetdato, Constants.DEFAULT_LEVETID_OPPRETTET_SOKNAD)
	}
	return mapTilOffsetDateTime(
		soknadDbData.forsteinnsendingsdato ?: soknadDbData.opprettetdato,
		soknadDbData.ettersendingsfrist ?: Constants.DEFAULT_FRIST_FOR_ETTERSENDELSE
	)
}

fun mapTilOffsetDateTime(localDateTime: LocalDateTime?): OffsetDateTime? =
	if (localDateTime == null) null else localDateTime.atOffset(ZoneId.of("CET").rules.getOffset(localDateTime))

fun mapTilOffsetDateTime(localDateTime: LocalDateTime, offset: Long): OffsetDateTime {
	if (offset < 0) {
		return localDateTime.atOffset(ZoneId.of("CET").rules.getOffset(localDateTime)).minusDays(offset.absoluteValue)
	}
	return localDateTime.atOffset(ZoneId.of("CET").rules.getOffset(localDateTime)).plusDays(offset)
}

fun mapTilLocalDateTime(offsetDateTime: OffsetDateTime?): LocalDateTime? =
	offsetDateTime?.toLocalDateTime()

fun mapTilFilDb(filDto: FilDto) = FilDbData(
	filDto.id,
	filDto.vedleggsid,
	filDto.filnavn ?: "",
	mapTilDbMimetype(filDto.mimetype) ?: "application/pdf",
	if (filDto.data == null) null else filDto.data?.size,
	filDto.data,
	mapTilLocalDateTime(filDto.opprettetdato) ?: LocalDateTime.now()
)

fun mapTilVedleggDb(vedleggDto: VedleggDto, soknadsId: Long) =
	mapTilVedleggDb(vedleggDto, soknadsId, vedleggDto.skjemaurl, mapTilDbOpplastingsStatus(vedleggDto.opplastingsStatus))

fun mapTilVedleggDb(vedleggDto: VedleggDto, soknadsId: Long, vedleggsId: Long) =
	mapTilVedleggDb(
		vedleggDto,
		soknadsId,
		vedleggDto.skjemaurl,
		mapTilDbOpplastingsStatus(vedleggDto.opplastingsStatus),
		vedleggsId
	)

fun mapTilVedleggDb(
	vedleggDto: VedleggDto,
	soknadsId: Long,
	url: String?,
	opplastingsStatus: OpplastingsStatus,
	vedleggsId: Long? = null
) =
	VedleggDbData(
		id = vedleggsId ?: vedleggDto.id,
		soknadsid = soknadsId,
		status = opplastingsStatus,
		erhoveddokument = vedleggDto.erHoveddokument,
		ervariant = vedleggDto.erVariant,
		erpdfa = vedleggDto.erPdfa,
		erpakrevd = vedleggDto.erPakrevd,
		vedleggsnr = vedleggDto.vedleggsnr,
		tittel = vedleggDto.tittel,
		label = vedleggDto.label,
		beskrivelse = vedleggDto.beskrivelse,
		mimetype = mapTilDbMimetype(vedleggDto.mimetype),
		uuid = vedleggDto.uuid ?: UUID.randomUUID().toString(),
		opprettetdato = mapTilLocalDateTime(vedleggDto.opprettetdato)!!,
		endretdato = LocalDateTime.now(),
		innsendtdato = mapTilLocalDateTime(vedleggDto.innsendtdato),
		vedleggsurl = url ?: vedleggDto.skjemaurl,
		formioid = vedleggDto.formioId
	)

fun oppdaterVedleggDb(vedleggDbData: VedleggDbData, patchVedleggDto: PatchVedleggDto): VedleggDbData =
	VedleggDbData(
		id = vedleggDbData.id,
		soknadsid = vedleggDbData.soknadsid,
		status = if (patchVedleggDto.opplastingsStatus == null) vedleggDbData.status else mapTilDbOpplastingsStatus(
			patchVedleggDto.opplastingsStatus!!
		),
		erhoveddokument = vedleggDbData.erhoveddokument,
		ervariant = vedleggDbData.ervariant,
		erpdfa = vedleggDbData.erpdfa,
		erpakrevd = vedleggDbData.erpakrevd,
		vedleggsnr = vedleggDbData.vedleggsnr,
		tittel = patchVedleggDto.tittel ?: vedleggDbData.tittel,
		label = patchVedleggDto.tittel ?: vedleggDbData.label,
		beskrivelse = vedleggDbData.beskrivelse,
		mimetype = vedleggDbData.mimetype,
		uuid = vedleggDbData.uuid ?: UUID.randomUUID().toString(),
		opprettetdato = vedleggDbData.opprettetdato,
		endretdato = LocalDateTime.now(),
		innsendtdato = vedleggDbData.innsendtdato,
		vedleggsurl = vedleggDbData.vedleggsurl,
		formioid = vedleggDbData.formioid
	)


fun mapTilSoknadDb(
	dokumentSoknadDto: DokumentSoknadDto,
	innsendingsId: String,
	status: SoknadsStatus = SoknadsStatus.Opprettet,
	id: Long? = null
) =
	SoknadDbData(
		id = id ?: dokumentSoknadDto.id,
		innsendingsid = innsendingsId,
		tittel = dokumentSoknadDto.tittel,
		skjemanr = dokumentSoknadDto.skjemanr,
		tema = dokumentSoknadDto.tema,
		spraak = dokumentSoknadDto.spraak ?: "no",
		status = mapTilSoknadsStatus(dokumentSoknadDto.status, status),
		brukerid = dokumentSoknadDto.brukerId,
		ettersendingsid = dokumentSoknadDto.ettersendingsId,
		opprettetdato = mapTilLocalDateTime(dokumentSoknadDto.opprettetDato)!!,
		endretdato = LocalDateTime.now(),
		innsendtdato = if (status == SoknadsStatus.Innsendt) LocalDateTime.now() else mapTilLocalDateTime(dokumentSoknadDto.innsendtDato),
		visningssteg = dokumentSoknadDto.visningsSteg,
		visningstype = dokumentSoknadDto.visningsType,
		kanlasteoppannet = dokumentSoknadDto.kanLasteOppAnnet ?: true,
		forsteinnsendingsdato = mapTilLocalDateTime(dokumentSoknadDto.forsteInnsendingsDato),
		ettersendingsfrist = dokumentSoknadDto.fristForEttersendelse,
		arkiveringsstatus = mapTilDbArkiveringsStatus(dokumentSoknadDto.arkiveringsStatus ?: ArkiveringsStatusDto.ikkeSatt)
	)

private fun mapTilArkiveringsStatusDto(arkiveringsStatus: ArkiveringsStatus): ArkiveringsStatusDto =
	when (arkiveringsStatus) {
		ArkiveringsStatus.IkkeSatt -> ArkiveringsStatusDto.ikkeSatt
		ArkiveringsStatus.Arkivert -> ArkiveringsStatusDto.arkivert
		ArkiveringsStatus.ArkiveringFeilet -> ArkiveringsStatusDto.arkiveringFeilet
	}

private fun mapTilDbArkiveringsStatus(arkiveringsStatusDto: ArkiveringsStatusDto): ArkiveringsStatus =
	when (arkiveringsStatusDto) {
		ArkiveringsStatusDto.ikkeSatt -> ArkiveringsStatus.IkkeSatt
		ArkiveringsStatusDto.arkivert -> ArkiveringsStatus.Arkivert
		ArkiveringsStatusDto.arkiveringFeilet -> ArkiveringsStatus.ArkiveringFeilet
	}

fun mapTilSoknadsStatus(soknadsStatus: SoknadsStatusDto?, newStatus: SoknadsStatus?): SoknadsStatus {
	return newStatus ?: when (soknadsStatus) {
		SoknadsStatusDto.opprettet -> SoknadsStatus.Opprettet
		SoknadsStatusDto.utfylt -> SoknadsStatus.Utfylt
		SoknadsStatusDto.innsendt -> SoknadsStatus.Innsendt
		SoknadsStatusDto.slettetAvBruker -> SoknadsStatus.SlettetAvBruker
		SoknadsStatusDto.automatiskSlettet -> SoknadsStatus.AutomatiskSlettet
		else -> SoknadsStatus.Opprettet
	}
}

fun mapTilSoknadsStatusDto(soknadsStatus: SoknadsStatus?): SoknadsStatusDto? =
	when (soknadsStatus) {
		SoknadsStatus.Opprettet -> SoknadsStatusDto.opprettet
		SoknadsStatus.Utfylt -> SoknadsStatusDto.utfylt
		SoknadsStatus.Innsendt -> SoknadsStatusDto.innsendt
		SoknadsStatus.SlettetAvBruker -> SoknadsStatusDto.slettetAvBruker
		SoknadsStatus.AutomatiskSlettet -> SoknadsStatusDto.automatiskSlettet
		else -> null
	}

fun mapTilOpplastingsStatusDto(opplastingsStatus: OpplastingsStatus): OpplastingsStatusDto =
	when (opplastingsStatus) {
		OpplastingsStatus.IKKE_VALGT -> OpplastingsStatusDto.ikkeValgt
		OpplastingsStatus.SEND_SENERE -> OpplastingsStatusDto.sendSenere
		OpplastingsStatus.LASTET_OPP -> OpplastingsStatusDto.lastetOpp
		OpplastingsStatus.INNSENDT -> OpplastingsStatusDto.innsendt
		OpplastingsStatus.SENDES_AV_ANDRE -> OpplastingsStatusDto.sendesAvAndre
		OpplastingsStatus.SENDES_IKKE -> OpplastingsStatusDto.sendesIkke
		else -> OpplastingsStatusDto.ikkeValgt
	}

fun mapTilDbOpplastingsStatus(opplastingsStatusDto: OpplastingsStatusDto): OpplastingsStatus =
	when (opplastingsStatusDto) {
		OpplastingsStatusDto.ikkeValgt -> OpplastingsStatus.IKKE_VALGT
		OpplastingsStatusDto.sendSenere -> OpplastingsStatus.SEND_SENERE
		OpplastingsStatusDto.lastetOpp -> OpplastingsStatus.LASTET_OPP
		OpplastingsStatusDto.innsendt -> OpplastingsStatus.INNSENDT
		OpplastingsStatusDto.sendesAvAndre -> OpplastingsStatus.SENDES_AV_ANDRE
		OpplastingsStatusDto.sendesIkke -> OpplastingsStatus.SENDES_IKKE
	}

fun mapTilMimetype(mimeString: String?): Mimetype? =
	when (mimeString) {
		"application/pdf" -> Mimetype.applicationSlashPdf
		"application/json" -> Mimetype.applicationSlashJson
		"application/jpeg" -> Mimetype.imageSlashJpeg
		"application/png" -> Mimetype.imageSlashPng
		else -> null
	}

fun mapTilDbMimetype(mimetype: Mimetype?): String? =
	when (mimetype) {
		Mimetype.applicationSlashPdf -> "application/pdf"
		Mimetype.applicationSlashJson -> "application/json"
		Mimetype.imageSlashJpeg -> "application/jpeg"
		Mimetype.imageSlashPng -> "application/png"
		else -> null
	}

fun mapTilSkjemaDto(dokumentSoknadDto: DokumentSoknadDto): SkjemaDto {
	val hovedDokument = dokumentSoknadDto.hoveddokument
	val hovedDokumentVariant = dokumentSoknadDto.hoveddokumentVariant
	val vedleggsListe = dokumentSoknadDto.vedleggsListeUtenHoveddokument.map { mapTilSkjemaDokumentDto(it) }

	if (hovedDokument == null || hovedDokumentVariant == null) {
		throw BackendErrorException("Hoveddokument eller variant mangler", "Finner ikke hoveddokument i vedleggsliste")
	}

	return SkjemaDto(
		innsendingsId = dokumentSoknadDto.innsendingsId,
		brukerId = dokumentSoknadDto.brukerId,
		skjemanr = dokumentSoknadDto.skjemanr,
		tittel = dokumentSoknadDto.tittel,
		tema = dokumentSoknadDto.tema,
		spraak = dokumentSoknadDto.spraak ?: "no",
		status = dokumentSoknadDto.status,
		hoveddokument = mapTilSkjemaDokumentDto(hovedDokument),
		hoveddokumentVariant = mapTilSkjemaDokumentDto(hovedDokumentVariant),
		vedleggsListe = vedleggsListe,
		kanLasteOppAnnet = dokumentSoknadDto.kanLasteOppAnnet,
		fristForEttersendelse = dokumentSoknadDto.fristForEttersendelse,
	)
}

fun mapTilSkjemaDokumentDto(vedleggDto: VedleggDto): SkjemaDokumentDto {
	val vedleggsnr =
		vedleggDto.vedleggsnr ?: throw BackendErrorException("Vedleggsnr mangler", "Finner ikke vedleggsnr i vedlegg")

	return SkjemaDokumentDto(
		vedleggsnr = vedleggsnr,
		tittel = vedleggDto.tittel,
		label = vedleggDto.label,
		beskrivelse = vedleggDto.beskrivelse,
		mimetype = vedleggDto.mimetype,
		pakrevd = vedleggDto.erPakrevd,
		document = vedleggDto.document,
		formioId = vedleggDto.formioId,
	)
}

fun translate(soknadDto: DokumentSoknadDto, vedleggDtos: List<VedleggDto>): Soknad {
	return Soknad(
		soknadDto.innsendingsId!!,
		soknadDto.ettersendingsId != null,
		soknadDto.brukerId,
		soknadDto.tema,
		translate(vedleggDtos)
	)
}

fun translate(vedleggDtos: List<VedleggDto>): List<DocumentData> {
	/*
	Mappe fra liste av vedleggdto til en liste av dokument inneholdene liste av varianter.
	Det er antatt at det kun er hoveddokumentet som vil ha varianter.
	Vedleggdto inneholder både dokument- og vedlegginfo
	 */
	// Lag documentdata for hoveddokumentet (finn alle vedleggdto markert som hoveddokument)
	val hoveddokumentVedlegg: List<Varianter> = vedleggDtos
		.filter { it.erHoveddokument && it.opplastingsStatus == OpplastingsStatusDto.lastetOpp }
		.map { translate(it) }

	val hovedDokument: DocumentData = vedleggDtos
		.filter { it.erHoveddokument && it.opplastingsStatus == OpplastingsStatusDto.lastetOpp && !it.erVariant }
		.map { DocumentData(it.vedleggsnr!!, it.erHoveddokument, it.tittel, hoveddokumentVedlegg) }
		.first()

	// Merk: at det  er antatt at vedlegg ikke har varianter. Hvis vi skal støtte dette må varianter av samme vedlegg linkes sammen
	val vedlegg: List<DocumentData> = vedleggDtos
		.filter { !it.erHoveddokument && it.opplastingsStatus == OpplastingsStatusDto.lastetOpp }
		.map { DocumentData(it.vedleggsnr!!, it.erHoveddokument, it.tittel, listOf(translate(it))) }

	return listOf(hovedDokument) + vedlegg
}

fun translate(dokumentDto: VedleggDto): Varianter {
	return Varianter(
		dokumentDto.uuid!!, dokumentDto.mimetype?.value ?: "application/pdf",
		(dokumentDto.vedleggsnr ?: "N6") + "." + filExtention(dokumentDto),
		filExtention(dokumentDto)
	)
}

fun filExtention(dokumentDto: VedleggDto): String =
	when (dokumentDto.mimetype) {
		Mimetype.imageSlashPng -> "png"
		Mimetype.imageSlashJpeg -> "jpeg"
		Mimetype.applicationSlashJson -> "json"
		Mimetype.applicationSlashPdf -> if (dokumentDto.erPdfa) "pdfa" else "pdf"
		else -> ""
	}

fun maskerFnr(soknad: Soknad): Soknad {
	return Soknad(soknad.innsendingId, soknad.erEttersendelse, personId = "*****", soknad.tema, soknad.dokumenter)
}
