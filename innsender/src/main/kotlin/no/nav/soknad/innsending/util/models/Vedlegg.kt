package no.nav.soknad.innsending.util.models

import no.nav.soknad.innsending.repository.VedleggDbData

val List<VedleggDbData>.hovedDokumentVariant: VedleggDbData?
	get() {
		return this.find { it.erhoveddokument && it.ervariant }
	}

val List<VedleggDbData>.hovedDokument: VedleggDbData?
	get() {
		return this.find { it.erhoveddokument && !it.ervariant }
	}
