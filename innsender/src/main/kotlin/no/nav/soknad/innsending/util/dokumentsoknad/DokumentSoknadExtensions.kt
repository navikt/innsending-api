package no.nav.soknad.innsending.util.dokumentsoknad

import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.util.models.vedleggsListeUtenHoveddokument

fun DokumentSoknadDto.isLospost() = this.visningsType == VisningsType.lospost

fun DokumentSoknadDto.getMissingRequiredAttachments(): List<VedleggDto> {
		return this.vedleggsListeUtenHoveddokument.filter { it.isMissingAndRequired() }
}

fun VedleggDto.isMissingAndRequired(): Boolean {
	val isNotHoveddokument = !this.erHoveddokument
	val isRequiredN6Vedlegg = this.erPakrevd && this.vedleggsnr == "N6"
	val isNotN6Vedlegg = this.vedleggsnr != "N6"
	val hasStatusSendSenereEllerIkkevalgt =
		this.opplastingsStatus == OpplastingsStatusDto.SendSenere || this.opplastingsStatus == OpplastingsStatusDto.IkkeValgt

	return isNotHoveddokument && (isRequiredN6Vedlegg || isNotN6Vedlegg) && hasStatusSendSenereEllerIkkevalgt
}
