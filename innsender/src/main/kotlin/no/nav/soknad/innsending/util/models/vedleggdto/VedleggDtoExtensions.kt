package no.nav.soknad.innsending.util.models.vedleggdto

import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.VedleggDto
import kotlin.collections.plus

fun VedleggDto.isMissingAndRequired(): Boolean {
	val isNotHoveddokument = !this.erHoveddokument
	val isRequiredN6Vedlegg = this.erPakrevd && this.vedleggsnr == "N6"
	val isNotN6Vedlegg = this.vedleggsnr != "N6"
	val hasStatusSendSenereEllerIkkevalgt =
		this.opplastingsStatus == OpplastingsStatusDto.SendSenere || this.opplastingsStatus == OpplastingsStatusDto.IkkeValgt

	return isNotHoveddokument && (isRequiredN6Vedlegg || isNotN6Vedlegg) && hasStatusSendSenereEllerIkkevalgt
}

operator fun VedleggDto.plus(other: VedleggDto): List<VedleggDto> = listOf(this, other)
operator fun VedleggDto.plus(list: List<VedleggDto>): List<VedleggDto> = listOf(this) + list
operator fun List<VedleggDto>.plus(other: VedleggDto): List<VedleggDto> = this + listOf(other)
