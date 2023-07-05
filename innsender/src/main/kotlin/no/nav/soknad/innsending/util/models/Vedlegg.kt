package no.nav.soknad.innsending.util.models

import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.repository.domain.models.VedleggDbData


val List<VedleggDbData>.hovedDokument: VedleggDbData?
	get() {
		return this.find { it.erhoveddokument && !it.ervariant }
	}

val List<VedleggDbData>.hovedDokumentVariant: VedleggDbData?
	get() {
		return this.find { it.erhoveddokument && it.ervariant }
	}

val List<VedleggDto>.hovedDokument: VedleggDto?
	get() {
		return this.find { it.erHoveddokument && !it.erVariant }
	}

val List<VedleggDto>.hovedDokumentVariant: VedleggDto?
	get() {
		return this.find { it.erHoveddokument && it.erVariant }
	}
