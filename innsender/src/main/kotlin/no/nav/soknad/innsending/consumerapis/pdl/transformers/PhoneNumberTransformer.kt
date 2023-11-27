package no.nav.soknad.innsending.consumerapis.pdl.transformers

import no.nav.soknad.innsending.pdl.generated.prefilldata.Telefonnummer


object PhoneNumberTransformer {
	fun transformPhoneNumbers(phoneNumbers: List<Telefonnummer>?): String {
		val prioritizedNumber = phoneNumbers?.minByOrNull { it.prioritet } ?: phoneNumbers?.firstOrNull()

		val trimmedNumber = prioritizedNumber?.nummer?.trim()?.replace(" ", "")
		val trimmedLandskode = prioritizedNumber?.landskode?.trim()?.replace(" ", "")

		return "${trimmedLandskode}${trimmedNumber}"
	}

}
