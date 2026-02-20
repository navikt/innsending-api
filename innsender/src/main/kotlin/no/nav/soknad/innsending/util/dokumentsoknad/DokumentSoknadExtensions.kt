package no.nav.soknad.innsending.util.dokumentsoknad

import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.util.models.vedleggdto.isMissingAndRequired
import no.nav.soknad.innsending.util.models.vedleggsListeUtenHoveddokument

fun DokumentSoknadDto.isLospost() = this.visningsType == VisningsType.lospost

fun DokumentSoknadDto.getMissingRequiredAttachments(): List<VedleggDto> {
		return this.vedleggsListeUtenHoveddokument.filter { it.isMissingAndRequired() }
}
