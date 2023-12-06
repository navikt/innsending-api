package no.nav.soknad.innsending.utils.builders.pdl

import no.nav.soknad.innsending.pdl.generated.prefilldata.Endring

class MetadataTestBuilder {
	private var endringer: List<Endring> = emptyList()
	private var master: String = ""
	private var historisk: Boolean = false

	fun endringer(endringer: List<Endring>) = apply { this.endringer = endringer }
	fun master(master: String) = apply { this.master = master }
	fun historisk(historisk: Boolean) = apply { this.historisk = historisk }

	fun build() = no.nav.soknad.innsending.pdl.generated.prefilldata.Metadata(
		endringer = endringer,
		master = master,
		historisk = historisk
	)
}
