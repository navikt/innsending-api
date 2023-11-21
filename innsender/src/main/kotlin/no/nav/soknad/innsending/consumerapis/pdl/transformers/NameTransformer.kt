package no.nav.soknad.innsending.consumerapis.pdl.transformers

import no.nav.soknad.innsending.pdl.generated.prefilldata.Navn
import java.time.LocalDate
import java.time.format.DateTimeFormatter


object NameTransformer {
	fun transformName(names: List<Navn>?): Navn? {
		return findCurrentName(names)
	}

	// Find the latest name that is valid today. Use the first name if no other is found
	private fun findCurrentName(names: List<Navn>?): Navn? {
		if (names.isNullOrEmpty()) return null
		val today = LocalDate.now()

		return names
			.filter {
				it.gyldigFraOgMed != null && LocalDate.parse(it.gyldigFraOgMed, DateTimeFormatter.ISO_LOCAL_DATE) <= today
			}
			.maxByOrNull { navn -> LocalDate.parse(navn.gyldigFraOgMed, DateTimeFormatter.ISO_LOCAL_DATE) }
			?: names.firstOrNull { it.gyldigFraOgMed == null }
	}
}
