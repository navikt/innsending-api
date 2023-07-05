package no.nav.soknad.innsending.util.mapping

import no.nav.soknad.arkivering.soknadsmottaker.model.DocumentData
import no.nav.soknad.arkivering.soknadsmottaker.model.Varianter
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.OpplastingsStatus
import no.nav.soknad.innsending.repository.VedleggDbData
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

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

fun mapTilSkjemaDokumentDto(vedleggDto: VedleggDto): SkjemaDokumentDto {
	val vedleggsnr =
		vedleggDto.vedleggsnr ?: throw BackendErrorException("Vedleggsnr mangler. Finner ikke vedleggsnr i vedlegg")

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
