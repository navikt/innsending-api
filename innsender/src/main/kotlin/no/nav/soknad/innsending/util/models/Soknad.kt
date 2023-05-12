package no.nav.soknad.innsending.util.models

import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.VedleggDto

val DokumentSoknadDto.hoveddokument: VedleggDto?
	get() = vedleggsListe.find {
		it.erHoveddokument && !it.erVariant
	}

val DokumentSoknadDto.hoveddokumentVariant: VedleggDto?
	get() = vedleggsListe.find {
		it.erHoveddokument && it.erVariant
	}

val DokumentSoknadDto.vedleggsListeUtenHoveddokument: List<VedleggDto>
	get() = vedleggsListe.filter {
		!it.erHoveddokument
	}
