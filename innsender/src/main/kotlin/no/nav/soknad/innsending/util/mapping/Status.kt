package no.nav.soknad.innsending.util.mapping

import no.nav.soknad.innsending.model.ArkiveringsStatusDto
import no.nav.soknad.innsending.model.FilDto
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.OpplastingsStatus
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus

fun mapTilArkiveringsStatusDto(arkiveringsStatus: ArkiveringsStatus): ArkiveringsStatusDto =
	when (arkiveringsStatus) {
		ArkiveringsStatus.IkkeSatt -> ArkiveringsStatusDto.IkkeSatt
		ArkiveringsStatus.Arkivert -> ArkiveringsStatusDto.Arkivert
		ArkiveringsStatus.ArkiveringFeilet -> ArkiveringsStatusDto.ArkiveringFeilet
	}

fun mapTilDbArkiveringsStatus(arkiveringsStatusDto: ArkiveringsStatusDto): ArkiveringsStatus =
	when (arkiveringsStatusDto) {
		ArkiveringsStatusDto.IkkeSatt -> ArkiveringsStatus.IkkeSatt
		ArkiveringsStatusDto.Arkivert -> ArkiveringsStatus.Arkivert
		ArkiveringsStatusDto.ArkiveringFeilet -> ArkiveringsStatus.ArkiveringFeilet
	}

fun mapTilSoknadsStatus(soknadsStatus: SoknadsStatusDto?, newStatus: SoknadsStatus?): SoknadsStatus {
	return newStatus ?: when (soknadsStatus) {
		SoknadsStatusDto.Opprettet -> SoknadsStatus.Opprettet
		SoknadsStatusDto.Utfylt -> SoknadsStatus.Utfylt
		SoknadsStatusDto.Innsendt -> SoknadsStatus.Innsendt
		SoknadsStatusDto.SlettetAvBruker -> SoknadsStatus.SlettetAvBruker
		SoknadsStatusDto.AutomatiskSlettet -> SoknadsStatus.AutomatiskSlettet
		else -> SoknadsStatus.Opprettet
	}
}

fun mapTilSoknadsStatusDto(soknadsStatus: SoknadsStatus?): SoknadsStatusDto? =
	when (soknadsStatus) {
		SoknadsStatus.Opprettet -> SoknadsStatusDto.Opprettet
		SoknadsStatus.Utfylt -> SoknadsStatusDto.Utfylt
		SoknadsStatus.Innsendt -> SoknadsStatusDto.Innsendt
		SoknadsStatus.SlettetAvBruker -> SoknadsStatusDto.SlettetAvBruker
		SoknadsStatus.AutomatiskSlettet -> SoknadsStatusDto.AutomatiskSlettet
		else -> null
	}

fun mapTilOpplastingsStatusDto(opplastingsStatus: OpplastingsStatus): OpplastingsStatusDto =
	when (opplastingsStatus) {
		OpplastingsStatus.IKKE_VALGT -> OpplastingsStatusDto.IkkeValgt
		OpplastingsStatus.SEND_SENERE -> OpplastingsStatusDto.SendSenere
		OpplastingsStatus.LASTET_OPP -> OpplastingsStatusDto.LastetOpp
		OpplastingsStatus.INNSENDT -> OpplastingsStatusDto.Innsendt
		OpplastingsStatus.SENDES_AV_ANDRE -> OpplastingsStatusDto.SendesAvAndre
		OpplastingsStatus.SENDES_IKKE -> OpplastingsStatusDto.SendesIkke
		OpplastingsStatus.LASTET_OPP_IKKE_RELEVANT_LENGER -> OpplastingsStatusDto.LastetOppIkkeRelevantLenger
		else -> OpplastingsStatusDto.IkkeValgt
	}

fun mapTilDbOpplastingsStatus(opplastingsStatusDto: OpplastingsStatusDto): OpplastingsStatus =
	when (opplastingsStatusDto) {
		OpplastingsStatusDto.IkkeValgt -> OpplastingsStatus.IKKE_VALGT
		OpplastingsStatusDto.SendSenere -> OpplastingsStatus.SEND_SENERE
		OpplastingsStatusDto.LastetOpp -> OpplastingsStatus.LASTET_OPP
		OpplastingsStatusDto.Innsendt -> OpplastingsStatus.INNSENDT
		OpplastingsStatusDto.SendesAvAndre -> OpplastingsStatus.SENDES_AV_ANDRE
		OpplastingsStatusDto.SendesIkke -> OpplastingsStatus.SENDES_IKKE
		OpplastingsStatusDto.LastetOppIkkeRelevantLenger -> OpplastingsStatus.LASTET_OPP_IKKE_RELEVANT_LENGER
		OpplastingsStatusDto.HarIkkeDokumentasjonen -> OpplastingsStatus.HAR_IKKE_DOKUMENTASJON
		OpplastingsStatusDto.LevertDokumentasjonTidligere -> OpplastingsStatus.LEVERT_DOKUMENTASJON_TIDLIGERE
		OpplastingsStatusDto.NavKanHenteDokumentasjon -> OpplastingsStatus.NAV_KAN_HENTE_DOKUMENTASJON
	}

fun avledOpplastingsstatusVedInnsending(filDto: FilDto?, vedleggDto: VedleggDto): OpplastingsStatusDto {
	// Dersom det er lastet opp en eller flere filer på vedlegget så skal filDto != null og størrelsen være satt
	if ((filDto != null) && (filDto.storrelse!! > 0)
		&& ((vedleggDto.opplastingsStatus == OpplastingsStatusDto.IkkeValgt) || (vedleggDto.opplastingsStatus == OpplastingsStatusDto.LastetOpp))
	) {
		return OpplastingsStatusDto.LastetOpp
	}
	return when (vedleggDto.opplastingsStatus) {
		OpplastingsStatusDto.IkkeValgt -> if (vedleggDto.erPakrevd) OpplastingsStatusDto.SendSenere else OpplastingsStatusDto.SendesIkke
		OpplastingsStatusDto.SendesAvAndre,
		OpplastingsStatusDto.SendSenere,
		OpplastingsStatusDto.Innsendt -> vedleggDto.opplastingsStatus

		else -> if (vedleggDto.erPakrevd) OpplastingsStatusDto.SendSenere else OpplastingsStatusDto.SendesIkke
	}
}
