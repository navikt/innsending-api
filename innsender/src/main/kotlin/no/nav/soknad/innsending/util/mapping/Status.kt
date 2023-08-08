package no.nav.soknad.innsending.util.mapping

import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.OpplastingsStatus
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus

fun mapTilArkiveringsStatusDto(arkiveringsStatus: ArkiveringsStatus): ArkiveringsStatusDto =
	when (arkiveringsStatus) {
		ArkiveringsStatus.IkkeSatt -> ArkiveringsStatusDto.ikkeSatt
		ArkiveringsStatus.Arkivert -> ArkiveringsStatusDto.arkivert
		ArkiveringsStatus.ArkiveringFeilet -> ArkiveringsStatusDto.arkiveringFeilet
	}

fun mapTilDbArkiveringsStatus(arkiveringsStatusDto: ArkiveringsStatusDto): ArkiveringsStatus =
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

fun avledOpplastingsstatusVedInnsending(filDto: FilDto?, vedleggDto: VedleggDto): OpplastingsStatusDto {
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
