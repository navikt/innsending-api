package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.consumerapis.skjema.KodeverkSkjema
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.*
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

public const val ukjentEttersendingsId = "-1" // sette lik innsendingsid istedenfor?

fun lagVedleggDtoMedOpplastetFil(filDto: FilDto?, vedleggDto: VedleggDto) =
	VedleggDto(vedleggDto.tittel, vedleggDto.label, vedleggDto.erHoveddokument,
		vedleggDto.erVariant, vedleggDto.erPdfa, vedleggDto.erPakrevd,
		avledOpplastingsstatus(filDto, vedleggDto),
		filDto?.opprettetdato ?: vedleggDto.opprettetdato, vedleggDto.id!!, vedleggDto.vedleggsnr, vedleggDto.beskrivelse,
		vedleggDto.uuid, filDto?.mimetype ?: vedleggDto.mimetype, filDto?.data, vedleggDto.skjemaurl
	)

private fun avledOpplastingsstatus(filDto: FilDto?, vedleggDto: VedleggDto): OpplastingsStatusDto {
	return if (filDto != null && filDto.data != null) {
		OpplastingsStatusDto.lastetOpp
	} else if (vedleggDto.opplastingsStatus == OpplastingsStatusDto.ikkeValgt) {
		if (vedleggDto.erPakrevd) OpplastingsStatusDto.sendSenere else OpplastingsStatusDto.sendesIkke
	} else
		if (vedleggDto.opplastingsStatus == OpplastingsStatusDto.sendesAvAndre || vedleggDto.opplastingsStatus == OpplastingsStatusDto.sendSenere || vedleggDto.opplastingsStatus == OpplastingsStatusDto.innsendt) {
			vedleggDto.opplastingsStatus
		} else {
			if (vedleggDto.erPakrevd) OpplastingsStatusDto.sendSenere	else OpplastingsStatusDto.sendesIkke
		}
}


fun lagFilDto(filDbData: FilDbData, medFil: Boolean = true) = FilDto(filDbData.vedleggsid, filDbData.id,
	filDbData.filnavn, mapTilMimetype(filDbData.mimetype), filDbData.storrelse,
	if (medFil) filDbData.data else null, filDbData.opprettetdato.atOffset(ZoneOffset.UTC))

fun lagVedleggDto(vedleggDbData: VedleggDbData, document: ByteArray? = null) =
	VedleggDto(vedleggDbData.tittel, vedleggDbData.label ?: "", vedleggDbData.erhoveddokument,
		vedleggDbData.ervariant, vedleggDbData.erpdfa, vedleggDbData.erpakrevd, mapTilOpplastingsStatusDto(vedleggDbData.status),
		mapTilOffsetDateTime(vedleggDbData.opprettetdato)!!, vedleggDbData.id!!, vedleggDbData.vedleggsnr, vedleggDbData.beskrivelse,
		vedleggDbData.uuid, mapTilMimetype(vedleggDbData.mimetype), document, vedleggDbData.vedleggsurl, )

fun lagDokumentSoknadDto(soknadDbData: SoknadDbData, vedleggDbDataListe: List<VedleggDbData>) =
	DokumentSoknadDto(soknadDbData.brukerid, soknadDbData.skjemanr, soknadDbData.tittel, soknadDbData.tema,
		mapTilSoknadsStatusDto(soknadDbData.status) ?: SoknadsStatusDto.opprettet, mapTilOffsetDateTime(soknadDbData.opprettetdato)!!,
		vedleggDbDataListe.map { lagVedleggDto(it) }, soknadDbData.id!!, soknadDbData.innsendingsid, soknadDbData.ettersendingsid,
		soknadDbData.spraak, mapTilOffsetDateTime(soknadDbData.endretdato), mapTilOffsetDateTime(soknadDbData.innsendtdato),
		soknadDbData.visningssteg ?: 0,
		soknadDbData.visningstype
			?: if (soknadDbData.ettersendingsid != null) VisningsType.ettersending else VisningsType.dokumentinnsending,
		soknadDbData.kanlasteoppannet ?: true
	)

fun mapTilOffsetDateTime(localDateTime: LocalDateTime?): OffsetDateTime? =
	localDateTime?.atOffset(ZoneOffset.UTC)

fun mapTilLocalDateTime(offsetDateTime: OffsetDateTime?): LocalDateTime? =
	offsetDateTime?.toLocalDateTime()

fun mapTilFilDb(filDto: FilDto) = FilDbData(filDto.id, filDto.vedleggsid, filDto.filnavn ?: ""
	, mapTilDbMimetype(filDto.mimetype) ?: "application/pdf"
	, if (filDto.data == null) null else filDto.data?.size, filDto.data
	, mapTilLocalDateTime(filDto.opprettetdato) ?: LocalDateTime.now())

fun mapTilVedleggDb(vedleggDto: VedleggDto, soknadsId: Long) =
	mapTilVedleggDb(vedleggDto, soknadsId, vedleggDto.skjemaurl, mapTilDbOpplastingsStatus(vedleggDto.opplastingsStatus))

fun mapTilVedleggDb(vedleggDto: VedleggDto, soknadsId: Long, opplastingsStatus: OpplastingsStatus) =
	mapTilVedleggDb(vedleggDto, soknadsId, vedleggDto.skjemaurl, opplastingsStatus)

fun mapTilVedleggDb(vedleggDto: VedleggDto, soknadsId: Long, kodeverkSkjema: KodeverkSkjema?) =
	mapTilVedleggDb(vedleggDto, soknadsId,  if (kodeverkSkjema != null ) kodeverkSkjema.url else vedleggDto.skjemaurl,
		mapTilDbOpplastingsStatus(vedleggDto.opplastingsStatus))

fun mapTilVedleggDb(vedleggDto: VedleggDto, soknadsId: Long, url: String?, opplastingsStatus: OpplastingsStatus) =
	VedleggDbData(vedleggDto.id, soknadsId, opplastingsStatus
		, vedleggDto.erHoveddokument, vedleggDto.erVariant, vedleggDto.erPdfa, vedleggDto.erPakrevd, vedleggDto.vedleggsnr
		, vedleggDto.tittel, vedleggDto.label, vedleggDto.beskrivelse
		, mapTilDbMimetype(vedleggDto.mimetype), vedleggDto.uuid ?: UUID.randomUUID().toString()
		, mapTilLocalDateTime(vedleggDto.opprettetdato)!!, LocalDateTime.now()
		, url ?: vedleggDto.skjemaurl
	)

fun oppdaterVedleggDb(vedleggDbData: VedleggDbData, patchVedleggDto: PatchVedleggDto): VedleggDbData =
	VedleggDbData(vedleggDbData.id, vedleggDbData.soknadsid,
		if (patchVedleggDto.opplastingsStatus == null) vedleggDbData.status else mapTilDbOpplastingsStatus(patchVedleggDto.opplastingsStatus!!)
		, vedleggDbData.erhoveddokument, vedleggDbData.ervariant, vedleggDbData.erpdfa, vedleggDbData.erpakrevd, vedleggDbData.vedleggsnr
		, patchVedleggDto.tittel ?: vedleggDbData.tittel, patchVedleggDto.tittel ?: vedleggDbData.label, vedleggDbData.beskrivelse
		, vedleggDbData.mimetype, vedleggDbData.uuid ?: UUID.randomUUID().toString()
		, vedleggDbData.opprettetdato, LocalDateTime.now()
		, vedleggDbData.vedleggsurl
	)


fun mapTilSoknadDb(dokumentSoknadDto: DokumentSoknadDto, innsendingsId: String, status: SoknadsStatus? = SoknadsStatus.Opprettet) =
	SoknadDbData(dokumentSoknadDto.id, innsendingsId,
		dokumentSoknadDto.tittel, dokumentSoknadDto.skjemanr, dokumentSoknadDto.tema, dokumentSoknadDto.spraak ?: "no",
		mapTilSoknadsStatus(dokumentSoknadDto.status, status), dokumentSoknadDto.brukerId, dokumentSoknadDto.ettersendingsId,
		mapTilLocalDateTime(dokumentSoknadDto.opprettetDato)!!, LocalDateTime.now(),
		if (status == SoknadsStatus.Innsendt) LocalDateTime.now()	else mapTilLocalDateTime(dokumentSoknadDto.innsendtDato),
		dokumentSoknadDto.visningsSteg, dokumentSoknadDto.visningsType, dokumentSoknadDto.kanLasteOppAnnet ?: true
	)

fun mapTilSoknadsStatus(soknadsStatus: SoknadsStatusDto?, newStatus: SoknadsStatus? ): SoknadsStatus {
	return newStatus ?:
	when (soknadsStatus) {
		SoknadsStatusDto.opprettet -> SoknadsStatus.Opprettet
		SoknadsStatusDto.innsendt -> SoknadsStatus.Innsendt
		SoknadsStatusDto.slettetAvBruker -> SoknadsStatus.SlettetAvBruker
		SoknadsStatusDto.automatiskSlettet -> SoknadsStatus.AutomatiskSlettet
		else -> SoknadsStatus.Opprettet
	}
}

fun mapTilSoknadsStatusDto(soknadsStatus: SoknadsStatus?): SoknadsStatusDto? =
	when (soknadsStatus) {
		SoknadsStatus.Opprettet -> SoknadsStatusDto.opprettet
		SoknadsStatus.Innsendt ->  SoknadsStatusDto.innsendt
		SoknadsStatus.SlettetAvBruker  -> SoknadsStatusDto.slettetAvBruker
		SoknadsStatus.AutomatiskSlettet -> SoknadsStatusDto.automatiskSlettet
		else -> null
	}

fun mapTilOpplastingsStatusDto(opplastingsStatus: OpplastingsStatus): OpplastingsStatusDto =
	when (opplastingsStatus) {
		OpplastingsStatus.IKKE_VALGT -> OpplastingsStatusDto.ikkeValgt
		OpplastingsStatus.SEND_SENERE ->  OpplastingsStatusDto.sendSenere
		OpplastingsStatus.LASTET_OPP  -> OpplastingsStatusDto.lastetOpp
		OpplastingsStatus.INNSENDT -> OpplastingsStatusDto.innsendt
		OpplastingsStatus.SENDES_AV_ANDRE -> OpplastingsStatusDto.sendesAvAndre
		OpplastingsStatus.SENDES_IKKE -> OpplastingsStatusDto.sendesIkke
		else -> OpplastingsStatusDto.ikkeValgt
	}

fun mapTilDbOpplastingsStatus(opplastingsStatusDto: OpplastingsStatusDto): OpplastingsStatus =
	when (opplastingsStatusDto) {
		OpplastingsStatusDto.ikkeValgt -> OpplastingsStatus.IKKE_VALGT
		OpplastingsStatusDto.sendSenere -> OpplastingsStatus.SEND_SENERE
		OpplastingsStatusDto.lastetOpp ->  OpplastingsStatus.LASTET_OPP
		OpplastingsStatusDto.innsendt -> OpplastingsStatus.INNSENDT
		OpplastingsStatusDto.sendesAvAndre -> OpplastingsStatus.SENDES_AV_ANDRE
		OpplastingsStatusDto.sendesIkke -> OpplastingsStatus.SENDES_IKKE
		else -> OpplastingsStatus.IKKE_VALGT
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
