package no.nav.soknad.innsending.util.mapping

import no.nav.soknad.arkivering.soknadsmottaker.model.Soknad
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.FilDbData
import no.nav.soknad.innsending.repository.domain.models.SoknadDbData
import no.nav.soknad.innsending.repository.domain.models.VedleggDbData
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.models.hoveddokument
import no.nav.soknad.innsending.util.models.hoveddokumentVariant
import no.nav.soknad.innsending.util.models.sletteDato
import no.nav.soknad.innsending.util.models.vedleggsListeUtenHoveddokument
import no.nav.soknad.innsending.util.soknaddbdata.getSkjemaPath
import java.time.LocalDateTime
import java.time.OffsetDateTime

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
		arkiveringsstatus = mapTilDbArkiveringsStatus(dokumentSoknadDto.arkiveringsStatus ?: ArkiveringsStatusDto.IkkeSatt),
		applikasjon = dokumentSoknadDto.applikasjon,
		skalslettesdato = dokumentSoknadDto.sletteDato!!,
		ernavopprettet = dokumentSoknadDto.erNavOpprettet
	)

fun lagDokumentSoknadDto(
	soknadDbData: SoknadDbData,
	vedleggDbDataListe: List<VedleggDbData>,
	erSystemGenerert: Boolean = false,
): DokumentSoknadDto {
	val erEttersending = soknadDbData.ettersendingsid != null || soknadDbData.visningstype == VisningsType.ettersending
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
		visningsType = soknadDbData.visningstype
			?: if (soknadDbData.ettersendingsid != null) VisningsType.ettersending else VisningsType.dokumentinnsending,
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
		visningsType = soknadDbData.visningstype
			?: if (soknadDbData.ettersendingsid != null) VisningsType.ettersending else VisningsType.dokumentinnsending,
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

fun translate(soknadDto: DokumentSoknadDto, vedleggDtos: List<VedleggDto>): Soknad {
	return Soknad(
		soknadDto.innsendingsId!!,
		soknadDto.ettersendingsId != null,
		soknadDto.brukerId,
		soknadDto.tema,
		translate(vedleggDtos)
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
