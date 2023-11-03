package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.pdl.generated.PrefillData
import no.nav.soknad.innsending.pdl.generated.prefilldata.Navn
import no.nav.soknad.innsending.pdl.generated.prefilldata.Person

class PrefilledPersonTestBuilder {
	private var fornavn: String? = "Test"
	private var etternavn: String? = "Testesen"

	fun fornavn(fornavn: String?) = apply { this.fornavn = fornavn }
	fun etternavn(etternavn: String?) = apply { this.etternavn = etternavn }

	fun build() = PrefillData.Result(
		Person(
			navn = listOf(Navn(fornavn ?: "", null, etternavn ?: "")),
			bostedsadresse = emptyList(),
			kjoenn = emptyList(),
			telefonnummer = emptyList()
		)
	)


}
