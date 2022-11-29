package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.*
import no.nav.soknad.innsending.util.Constants
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

const val ukjentEttersendingsId = "-1" // sette lik innsendingsid istedenfor?

fun erEttersending(dokumentSoknad: DokumentSoknadDto): Boolean =
	(dokumentSoknad.ettersendingsId != null) || (dokumentSoknad.visningsType == VisningsType.ettersending)

fun lagVedleggDtoMedOpplastetFil(filDto: FilDto?, vedleggDto: VedleggDto) =
	VedleggDto(tittel = vedleggDto.tittel, label = vedleggDto.label, erHoveddokument = vedleggDto.erHoveddokument,
		erVariant = vedleggDto.erVariant, erPdfa = vedleggDto.erPdfa, erPakrevd = vedleggDto.erPakrevd,
		opplastingsStatus = avledOpplastingsstatusVedInnsending(filDto, vedleggDto),
		opprettetdato = filDto?.opprettetdato ?: vedleggDto.opprettetdato, id = vedleggDto.id!!,
		vedleggsnr = vedleggDto.vedleggsnr, beskrivelse = vedleggDto.beskrivelse,
		uuid = vedleggDto.uuid, mimetype = filDto?.mimetype ?: vedleggDto.mimetype, document = filDto?.data, skjemaurl = vedleggDto.skjemaurl,
		innsendtdato = OffsetDateTime.now()
	)


private fun avledOpplastingsstatusVedInnsending(filDto: FilDto?, vedleggDto: VedleggDto): OpplastingsStatusDto {
	if (filDto?.data != null) return OpplastingsStatusDto.lastetOpp
	return when (vedleggDto.opplastingsStatus) {
		OpplastingsStatusDto.ikkeValgt -> if (vedleggDto.erPakrevd) OpplastingsStatusDto.sendSenere else OpplastingsStatusDto.sendesIkke
		OpplastingsStatusDto.sendesAvAndre,
		OpplastingsStatusDto.sendSenere,
		OpplastingsStatusDto.innsendt -> 	vedleggDto.opplastingsStatus
		else -> if (vedleggDto.erPakrevd) OpplastingsStatusDto.sendSenere	else OpplastingsStatusDto.sendesIkke
	}
}

fun lagFilDto(filDbData: FilDbData, medFil: Boolean = true) = FilDto(filDbData.vedleggsid, filDbData.id,
	filDbData.filnavn, mapTilMimetype(filDbData.mimetype), filDbData.storrelse,
	if (medFil) filDbData.data else null, filDbData.opprettetdato.atOffset(ZoneOffset.UTC))

fun lagVedleggDto(vedleggDbData: VedleggDbData, document: ByteArray? = null) =
	VedleggDto(vedleggDbData.tittel, vedleggDbData.label ?: "", vedleggDbData.erhoveddokument,
		vedleggDbData.ervariant, vedleggDbData.erpdfa, vedleggDbData.erpakrevd, mapTilOpplastingsStatusDto(vedleggDbData.status),
		mapTilOffsetDateTime(vedleggDbData.opprettetdato)!!, vedleggDbData.id!!, vedleggDbData.vedleggsnr, vedleggDbData.beskrivelse,
		vedleggDbData.uuid, mapTilMimetype(vedleggDbData.mimetype), document, vedleggDbData.vedleggsurl, mapTilOffsetDateTime(vedleggDbData.innsendtdato))

fun lagDokumentSoknadDto(soknadDbData: SoknadDbData, vedleggDbDataListe: List<VedleggDbData>) =
	DokumentSoknadDto(brukerId = soknadDbData.brukerid, skjemanr = soknadDbData.skjemanr, tittel = soknadDbData.tittel,
		tema = soknadDbData.tema, status = mapTilSoknadsStatusDto(soknadDbData.status) ?: SoknadsStatusDto.opprettet,
		vedleggsListe = vedleggDbDataListe.map { lagVedleggDto(it) }, id = soknadDbData.id!!, innsendingsId = soknadDbData.innsendingsid,
		ettersendingsId = soknadDbData.ettersendingsid,	spraak = soknadDbData.spraak,
		opprettetDato = mapTilOffsetDateTime(soknadDbData.opprettetdato)!!,
		endretDato = mapTilOffsetDateTime(soknadDbData.endretdato),
		innsendtDato = mapTilOffsetDateTime(soknadDbData.innsendtdato),
		visningsSteg = soknadDbData.visningssteg ?: 0,
		visningsType = soknadDbData.visningstype
			?: if (soknadDbData.ettersendingsid != null) VisningsType.ettersending else VisningsType.dokumentinnsending,
		kanLasteOppAnnet = soknadDbData.kanlasteoppannet ?: true,
		innsendingsFristDato = beregnInnsendingsFrist(soknadDbData),
		forsteInnsendingsDato = mapTilOffsetDateTime(soknadDbData.forsteinnsendingsdato),
		fristForEttersendelse = soknadDbData.ettersendingsfrist?: Constants.DEFAULT_FRIST_FOR_ETTERSENDELSE
	)

private fun beregnInnsendingsFrist(soknadDbData: SoknadDbData): OffsetDateTime {
	if (soknadDbData.ettersendingsid == null) {
		return mapTilOffsetDateTime(soknadDbData.opprettetdato, Constants.DEFAULT_LEVETID_OPPRETTET_SOKNAD)
	}
	return mapTilOffsetDateTime(soknadDbData.forsteinnsendingsdato ?: soknadDbData.opprettetdato,
		soknadDbData.ettersendingsfrist ?: Constants.DEFAULT_FRIST_FOR_ETTERSENDELSE)
}

fun mapTilOffsetDateTime(localDateTime: LocalDateTime?): OffsetDateTime? =
	localDateTime?.atOffset(ZoneOffset.UTC)

fun mapTilOffsetDateTime(localDateTime: LocalDateTime?, offset: Long): OffsetDateTime {
	if(localDateTime == null) return OffsetDateTime.now()
	return localDateTime.atOffset(ZoneOffset.UTC).plusDays(offset)
}

fun mapTilLocalDateTime(offsetDateTime: OffsetDateTime?): LocalDateTime? =
	offsetDateTime?.toLocalDateTime()

fun mapTilFilDb(filDto: FilDto) = FilDbData(filDto.id, filDto.vedleggsid, filDto.filnavn ?: ""
	, mapTilDbMimetype(filDto.mimetype) ?: "application/pdf"
	, if (filDto.data == null) null else filDto.data?.size, filDto.data
	, mapTilLocalDateTime(filDto.opprettetdato) ?: LocalDateTime.now())

fun mapTilVedleggDb(vedleggDto: VedleggDto, soknadsId: Long) =
	mapTilVedleggDb(vedleggDto, soknadsId, vedleggDto.skjemaurl, mapTilDbOpplastingsStatus(vedleggDto.opplastingsStatus))


fun mapTilVedleggDb(vedleggDto: VedleggDto, soknadsId: Long, url: String?, opplastingsStatus: OpplastingsStatus) =
	VedleggDbData(id = vedleggDto.id, soknadsid =  soknadsId, status = opplastingsStatus
		, erhoveddokument = vedleggDto.erHoveddokument, ervariant = vedleggDto.erVariant, erpdfa = vedleggDto.erPdfa, erpakrevd = vedleggDto.erPakrevd
		, vedleggsnr = vedleggDto.vedleggsnr, tittel = vedleggDto.tittel, label = vedleggDto.label, beskrivelse = vedleggDto.beskrivelse
		, mimetype =  mapTilDbMimetype(vedleggDto.mimetype), uuid = vedleggDto.uuid ?: UUID.randomUUID().toString()
		, opprettetdato = mapTilLocalDateTime(vedleggDto.opprettetdato)!!, endretdato = LocalDateTime.now(), innsendtdato = mapTilLocalDateTime(vedleggDto.innsendtdato)
		, vedleggsurl = url ?: vedleggDto.skjemaurl
	)

fun oppdaterVedleggDb(vedleggDbData: VedleggDbData, patchVedleggDto: PatchVedleggDto): VedleggDbData =
	VedleggDbData(vedleggDbData.id, vedleggDbData.soknadsid,
		if (patchVedleggDto.opplastingsStatus == null) vedleggDbData.status else mapTilDbOpplastingsStatus(patchVedleggDto.opplastingsStatus!!)
		, vedleggDbData.erhoveddokument, vedleggDbData.ervariant, vedleggDbData.erpdfa, vedleggDbData.erpakrevd, vedleggDbData.vedleggsnr
		, patchVedleggDto.tittel ?: vedleggDbData.tittel, patchVedleggDto.tittel ?: vedleggDbData.label, vedleggDbData.beskrivelse
		, vedleggDbData.mimetype, vedleggDbData.uuid ?: UUID.randomUUID().toString()
		, vedleggDbData.opprettetdato, LocalDateTime.now(), vedleggDbData.innsendtdato
		, vedleggDbData.vedleggsurl
	)


fun mapTilSoknadDb(dokumentSoknadDto: DokumentSoknadDto, innsendingsId: String, status: SoknadsStatus = SoknadsStatus.Opprettet) =
	SoknadDbData(id = dokumentSoknadDto.id, innsendingsid = innsendingsId,
		tittel = dokumentSoknadDto.tittel, skjemanr = dokumentSoknadDto.skjemanr, tema = dokumentSoknadDto.tema, spraak = dokumentSoknadDto.spraak ?: "no",
		status = mapTilSoknadsStatus(dokumentSoknadDto.status, status), brukerid = dokumentSoknadDto.brukerId, ettersendingsid = dokumentSoknadDto.ettersendingsId,
		opprettetdato = mapTilLocalDateTime(dokumentSoknadDto.opprettetDato)!!, endretdato = LocalDateTime.now(),
		innsendtdato = if (status == SoknadsStatus.Innsendt) LocalDateTime.now()	else mapTilLocalDateTime(dokumentSoknadDto.innsendtDato),
		visningssteg = dokumentSoknadDto.visningsSteg, visningstype = dokumentSoknadDto.visningsType, kanlasteoppannet = dokumentSoknadDto.kanLasteOppAnnet ?: true,
		forsteinnsendingsdato = mapTilLocalDateTime(dokumentSoknadDto.forsteInnsendingsDato), ettersendingsfrist = dokumentSoknadDto.fristForEttersendelse
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
