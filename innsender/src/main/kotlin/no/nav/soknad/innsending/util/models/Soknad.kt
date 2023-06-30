package no.nav.soknad.innsending.util.models

import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.model.VisningsType

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

val DokumentSoknadDto.kanGjoreEndringer: Boolean
	get() = status == SoknadsStatusDto.opprettet || status == SoknadsStatusDto.utfylt

val DokumentSoknadDto.erEtterSending: Boolean
	get() = ettersendingsId != null || visningsType == VisningsType.ettersending

