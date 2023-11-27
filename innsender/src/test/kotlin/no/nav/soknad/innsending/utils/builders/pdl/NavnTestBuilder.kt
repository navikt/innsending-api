package no.nav.soknad.innsending.utils.builders.pdl

import no.nav.soknad.innsending.pdl.generated.prefilldata.Metadata
import no.nav.soknad.innsending.pdl.generated.prefilldata.Navn
import no.nav.soknad.innsending.utils.Date
import java.time.LocalDateTime

class NavnTestBuilder {
	private var fornavn: String = "John"
	private var etternavn: String = "Doe"
	private var mellomnavn: String? = null
	private var gyldigFraOgMed: String? = Date.formatToLocalDate(LocalDateTime.now().minusDays(10))
	private var metadata: Metadata = MetadataTestBuilder().build()

	fun fornavn(fornavn: String) = apply { this.fornavn = fornavn }
	fun mellomnavn(mellomnavn: String?) = apply { this.mellomnavn = mellomnavn }
	fun etternavn(etternavn: String) = apply { this.etternavn = etternavn }
	fun gyldigFraOgMed(gyldigFraOgMed: String?) = apply { this.gyldigFraOgMed = gyldigFraOgMed }
	fun metadata(metadata: Metadata) = apply { this.metadata = metadata }

	fun build() = Navn(
		fornavn = fornavn,
		mellomnavn = mellomnavn,
		etternavn = etternavn,
		gyldigFraOgMed = gyldigFraOgMed,
		metadata = metadata
	)


}
