package no.nav.soknad.innsending.utils.builders.pdl

import no.nav.soknad.innsending.pdl.generated.enums.AdressebeskyttelseGradering
import no.nav.soknad.innsending.pdl.generated.prefilldata.Adressebeskyttelse
import no.nav.soknad.innsending.pdl.generated.prefilldata.Metadata

class AdressebeskyttelseTestBuilder {
	private var gradering: AdressebeskyttelseGradering = AdressebeskyttelseGradering.UGRADERT
	private var metadata: Metadata = MetadataTestBuilder().build()

	fun gradering(gradering: AdressebeskyttelseGradering) = apply { this.gradering = gradering }
	fun metadata(metadata: Metadata) = apply { this.metadata = metadata }

	fun build() = Adressebeskyttelse(gradering, metadata)
}
