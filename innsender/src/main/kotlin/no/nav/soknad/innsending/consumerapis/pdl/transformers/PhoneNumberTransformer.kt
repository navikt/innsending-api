package no.nav.soknad.innsending.consumerapis.pdl.transformers

import no.nav.soknad.innsending.pdl.generated.prefilldata.Telefonnummer


object PhoneNumberTransformer {
	fun transformPhoneNumbers(phoneNumbers: List<Telefonnummer>?): String {
		val prioritizedNumber = phoneNumbers?.find { it.prioritet == 1 } ?: phoneNumbers?.firstOrNull()
		return "${prioritizedNumber?.landskode}${prioritizedNumber?.nummer}"
	}

}
