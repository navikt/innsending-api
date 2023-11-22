package no.nav.soknad.innsending.utils.builders.pdl

import no.nav.soknad.innsending.pdl.generated.prefilldata.Metadata
import no.nav.soknad.innsending.pdl.generated.prefilldata.Telefonnummer

class TelefonnummerTestBuilder {
	private var landskode: String = "+47"
	private var nummer: String = "12345678"
	private var prioritet: Int = 1
	private var metadata = MetadataTestBuilder().build()

	fun landskode(landskode: String) = apply { this.landskode = landskode }
	fun nummer(nummer: String) = apply { this.nummer = nummer }
	fun prioritet(prioritet: Int) = apply { this.prioritet = prioritet }
	fun metadata(metadata: Metadata) = apply { this.metadata = metadata }

	fun build() = Telefonnummer(
		landskode = landskode,
		nummer = nummer,
		prioritet = prioritet,
		metadata = metadata,
	)

}
