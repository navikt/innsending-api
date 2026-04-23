package no.nav.soknad.innsending.util.mapping

import no.nav.soknad.arkivering.soknadsmottaker.model.Innsending
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.OpplastingsStatus
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.FilDbData
import no.nav.soknad.innsending.repository.domain.models.SoknadDbData
import no.nav.soknad.innsending.repository.domain.models.VedleggDbData
import no.nav.soknad.innsending.service.fillager.FileStorageNamespace
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.mapping.avsender.toArkiveringAvsenderDto
import no.nav.soknad.innsending.util.models.hoveddokument
import no.nav.soknad.innsending.util.models.hoveddokumentVariant
import no.nav.soknad.innsending.util.models.sletteDato
import no.nav.soknad.innsending.util.models.vedleggsListeUtenHoveddokument
import no.nav.soknad.innsending.util.soknaddbdata.getSkjemaPath
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

fun mapTilSoknadDb(
	dokumentSoknadDto: DokumentSoknadDto,
	innsendingsId: String,
	status: SoknadsStatus = SoknadsStatus.Opprettet,
	id: Long? = null,
	avsender: AvsenderDto? = null,
	bruker: BrukerDto? = null,
) =
	SoknadDbData(
		id = id ?: dokumentSoknadDto.id,
		innsendingsid = innsendingsId,
		tittel = dokumentSoknadDto.tittel,
		skjemanr = dokumentSoknadDto.skjemanr,
		tema = dokumentSoknadDto.tema,
		spraak = dokumentSoknadDto.spraak ?: "no",
		status = mapTilSoknadsStatus(dokumentSoknadDto.status, status),
		brukerid = bruker?.id?: dokumentSoknadDto.brukerId,
		ettersendingsid = dokumentSoknadDto.ettersendingsId,
		opprettetdato = mapTilLocalDateTime(dokumentSoknadDto.opprettetDato)!!,
		endretdato = LocalDateTime.now(),
		innsendtdato = if (status == SoknadsStatus.Innsendt) LocalDateTime.now() else mapTilLocalDateTime(dokumentSoknadDto.innsendtDato),
		visningssteg = dokumentSoknadDto.visningsSteg,
		visningstype = dokumentSoknadDto.visningsType,
		kanlasteoppannet = dokumentSoknadDto.kanLasteOppAnnet ?: true,
		forsteinnsendingsdato = mapTilLocalDateTime(dokumentSoknadDto.forsteInnsendingsDato),
		ettersendingsfrist = dokumentSoknadDto.fristForEttersendelse,
		arkiveringsstatus = mapTilDbArkiveringsStatus(dokumentSoknadDto.arkiveringsStatus ?: ArkiveringsStatusDto.IkkeSatt),
		applikasjon = dokumentSoknadDto.applikasjon,
		skalslettesdato = dokumentSoknadDto.sletteDato!!,
		ernavopprettet = dokumentSoknadDto.erNavOpprettet,
		brukertype = bruker?.idType ?: if (dokumentSoknadDto.brukerId != null) BrukerDto.IdType.FNR else null,
		avsender = avsender,
		affecteduser = null,
	)

fun lagDokumentSoknadDto(
	soknadDbData: SoknadDbData,
	vedleggDbDataListe: List<VedleggDbData>,
	erSystemGenerert: Boolean = false,
): DokumentSoknadDto {
	val erEttersending = soknadDbData.ettersendingsid != null || soknadDbData.visningstype == VisningsType.ettersending
	if (soknadDbData.visningstype == null) {
		throw IllegalStateException("Visningstype er null for soknad med id ${soknadDbData.id}")
	}
	return DokumentSoknadDto(
		brukerId = soknadDbData.brukerid,
		skjemanr = soknadDbData.skjemanr,
		tittel = soknadDbData.tittel,
		tema = soknadDbData.tema,
		status = mapTilSoknadsStatusDto(soknadDbData.status) ?: SoknadsStatusDto.Opprettet,
		vedleggsListe = vedleggDbDataListe.map { lagVedleggDto(it) },
		id = soknadDbData.id!!,
		innsendingsId = soknadDbData.innsendingsid,
		ettersendingsId = soknadDbData.ettersendingsid,
		spraak = soknadDbData.spraak,
		opprettetDato = mapTilOffsetDateTime(soknadDbData.opprettetdato)!!,
		endretDato = mapTilOffsetDateTime(soknadDbData.endretdato),
		innsendtDato = mapTilOffsetDateTime(soknadDbData.innsendtdato),
		visningsSteg = soknadDbData.visningssteg ?: 0,
		visningsType = soknadDbData.visningstype,
		kanLasteOppAnnet = soknadDbData.kanlasteoppannet ?: true,
		innsendingsFristDato = beregnInnsendingsFrist(soknadDbData),
		forsteInnsendingsDato = mapTilOffsetDateTime(soknadDbData.forsteinnsendingsdato),
		fristForEttersendelse = soknadDbData.ettersendingsfrist ?: Constants.DEFAULT_FRIST_FOR_ETTERSENDELSE,
		arkiveringsStatus = mapTilArkiveringsStatusDto(soknadDbData.arkiveringsstatus),
		erSystemGenerert = erSystemGenerert,
		soknadstype = if (erEttersending) SoknadType.ettersendelse else SoknadType.soknad,
		skjemaPath = soknadDbData.getSkjemaPath(),
		applikasjon = soknadDbData.applikasjon,
		skalSlettesDato = soknadDbData.skalslettesdato,
		erNavOpprettet = soknadDbData.ernavopprettet
	)
}


fun mapTilDokumentSoknadDto(
	soknadDbData: SoknadDbData,
	vedleggDbDataListe: List<VedleggDbData>,
	filerDbDataListe: List<FilDbData>,
): DokumentSoknadDto {
	val vedleggsListeMedFiler = vedleggDbDataListe.map { vedleggDbData ->
		val filer = filerDbDataListe.filter { filDbData ->
			filDbData.vedleggsid == vedleggDbData.id
		}
		lagVedleggDto(vedleggDbData, filer.firstOrNull()?.data)
	}
	val erEttersending = soknadDbData.ettersendingsid != null || soknadDbData.visningstype == VisningsType.ettersending
	if (soknadDbData.visningstype == null) {
		throw IllegalStateException("Visningstype er null for soknad med id ${soknadDbData.id}")
	}

	return DokumentSoknadDto(
		brukerId = soknadDbData.brukerid,
		skjemanr = soknadDbData.skjemanr,
		tittel = soknadDbData.tittel,
		tema = soknadDbData.tema,
		status = mapTilSoknadsStatusDto(soknadDbData.status) ?: SoknadsStatusDto.Opprettet,
		vedleggsListe = vedleggsListeMedFiler,
		id = soknadDbData.id!!,
		innsendingsId = soknadDbData.innsendingsid,
		ettersendingsId = soknadDbData.ettersendingsid,
		spraak = soknadDbData.spraak,
		opprettetDato = mapTilOffsetDateTime(soknadDbData.opprettetdato)!!,
		endretDato = mapTilOffsetDateTime(soknadDbData.endretdato),
		innsendtDato = mapTilOffsetDateTime(soknadDbData.innsendtdato),
		visningsSteg = soknadDbData.visningssteg ?: 0,
		visningsType = soknadDbData.visningstype,
		kanLasteOppAnnet = soknadDbData.kanlasteoppannet ?: true,
		innsendingsFristDato = beregnInnsendingsFrist(soknadDbData),
		forsteInnsendingsDato = mapTilOffsetDateTime(soknadDbData.forsteinnsendingsdato),
		fristForEttersendelse = soknadDbData.ettersendingsfrist ?: Constants.DEFAULT_FRIST_FOR_ETTERSENDELSE,
		arkiveringsStatus = mapTilArkiveringsStatusDto(soknadDbData.arkiveringsstatus),
		erSystemGenerert = false,
		soknadstype = if (erEttersending) SoknadType.ettersendelse else SoknadType.soknad,
		skjemaPath = soknadDbData.getSkjemaPath(),
		applikasjon = soknadDbData.applikasjon,
		skalSlettesDato = soknadDbData.skalslettesdato,
		erNavOpprettet = soknadDbData.ernavopprettet,
	)
}

fun mapTilSkjemaDto(dokumentSoknadDto: DokumentSoknadDto): SkjemaDto {
	val hovedDokument = dokumentSoknadDto.hoveddokument
	val hovedDokumentVariant = dokumentSoknadDto.hoveddokumentVariant
	val vedleggsListe = dokumentSoknadDto.vedleggsListeUtenHoveddokument.map { mapTilSkjemaDokumentDto(it) }
	val deletionDate = dokumentSoknadDto.sletteDato

	if (hovedDokument == null || hovedDokumentVariant == null) {
		throw BackendErrorException("Hoveddokument eller variant mangler. Finner ikke hoveddokument i vedleggsliste")
	}

	val emptySkjemaDokumentDto = SkjemaDokumentDto(vedleggsnr = "", tittel = "", label = "", pakrevd = false)

	return SkjemaDto(
		innsendingsId = dokumentSoknadDto.innsendingsId,
		brukerId = dokumentSoknadDto.brukerId,
		skjemanr = dokumentSoknadDto.skjemanr,
		tittel = dokumentSoknadDto.tittel,
		tema = dokumentSoknadDto.tema,
		spraak = dokumentSoknadDto.spraak ?: "no",
		status = dokumentSoknadDto.status,
		hoveddokument = if (hovedDokument == null) emptySkjemaDokumentDto else mapTilSkjemaDokumentDto(hovedDokument),
		hoveddokumentVariant = if (hovedDokumentVariant == null) emptySkjemaDokumentDto else mapTilSkjemaDokumentDto(
			hovedDokumentVariant
		),
		vedleggsListe = vedleggsListe,
		kanLasteOppAnnet = dokumentSoknadDto.kanLasteOppAnnet,
		fristForEttersendelse = dokumentSoknadDto.fristForEttersendelse,
		endretDato = dokumentSoknadDto.endretDato,
		skalSlettesDato = deletionDate,
		skjemaPath = dokumentSoknadDto.skjemaPath,
		visningsType = dokumentSoknadDto.visningsType,
		mellomlagringDager = dokumentSoknadDto.mellomlagringDager
	)
}

fun SubmitApplicationRequest.toDokumentSoknadDto(innsendingsId: UUID, clientId: String): SoknadDbData {
	val now = LocalDateTime.now()
	return SoknadDbData(
		id = null,
		innsendingsid = innsendingsId.toString(),
		tittel = this.title,
		skjemanr = this.formNumber,
		tema = this.tema,
		spraak = this.language,
		status = SoknadsStatus.Opprettet,
		brukerid = this.bruker,
		opprettetdato = now,
		endretdato = now,
		applikasjon = clientId,
		ettersendingsid = null,
		innsendtdato = null,
		visningssteg = null,
		visningstype = VisningsType.nologin,
		kanlasteoppannet = this.otherUploadAvailable,
		forsteinnsendingsdato = null,
		ettersendingsfrist = null,
		arkiveringsstatus = ArkiveringsStatus.IkkeSatt,
		skalslettesdato = now.plusDays(Constants.DEFAULT_LEVETID_OPPRETTET_SOKNAD).toOffsetDateTime(),
		ernavopprettet = false,
		brukertype = if (this.bruker != null) BrukerDto.IdType.FNR else null,
		avsender = this.avsender,
		affecteduser = null,
	)
}

fun SoknadDbData.createMainDocument(variant: Boolean = false, status: OpplastingsStatus = OpplastingsStatus.IKKE_VALGT): VedleggDbData {
	return VedleggDbData(
		id = null,
		soknadsid = this.id!!,
		vedleggsnr = this.skjemanr,
		status = status,
		erhoveddokument = true,
		ervariant = variant,
		erpdfa = !variant,
		erpakrevd = true,
		tittel = this.tittel,
		label = this.tittel,
		beskrivelse = null,
		mimetype = mapTilDbMimetype(if (variant) Mimetype.applicationSlashJson else Mimetype.applicationSlashPdf),
		uuid = UUID.randomUUID().toString(),
		opprettetdato = this.opprettetdato,
		endretdato = this.opprettetdato,
		innsendtdato = null,
		vedleggsurl = null,
		formioid = null,
		opplastingsvalgkommentarledetekst = null,
		opplastingsvalgkommentar = null,
		fileIds = null,
	)
}

fun DokumentSoknadDto.getFileStorageNamespace(): FileStorageNamespace = this.visningsType.getFileStorageNamespace()

fun VisningsType?.getFileStorageNamespace(): FileStorageNamespace {
	return when (this) {
		VisningsType.nologin -> FileStorageNamespace.NOLOGIN
		else -> FileStorageNamespace.DIGITAL
	}
}

fun translate(soknadDto: DokumentSoknadDto, vedleggDtos: List<VedleggDto>, avsenderDto: AvsenderDto, brukerDto: BrukerDto?): Innsending {
	return Innsending(
		innsendingsId = soknadDto.innsendingsId!!,
		ettersendelseTilId = soknadDto.ettersendingsId,
		kanal = if (soknadDto.visningsType == VisningsType.nologin) "NAV_NO_UINNLOGGET" else "NAV_NO",
		avsenderDto = avsenderDto.toArkiveringAvsenderDto(),
		brukerDto = brukerDto?.let { translate(it) },
		tema = soknadDto.tema,
		skjemanr = vedleggDtos.first{it.erHoveddokument}.vedleggsnr!!,
		tittel = vedleggDtos.first{it.erHoveddokument}.tittel,
		dokumenter = translate(vedleggDtos, true),
		innsendtDato = soknadDto.innsendtDato ?: mapTilOffsetDateTime(LocalDateTime.now())
	)
}
// Hjelpefunksjoner

private fun beregnInnsendingsFrist(soknadDbData: SoknadDbData): OffsetDateTime {
	if (soknadDbData.ettersendingsid == null) {
		return soknadDbData.skalslettesdato
	}
	return mapTilOffsetDateTime(
		soknadDbData.forsteinnsendingsdato ?: soknadDbData.opprettetdato,
		soknadDbData.ettersendingsfrist ?: Constants.DEFAULT_FRIST_FOR_ETTERSENDELSE
	)
}
