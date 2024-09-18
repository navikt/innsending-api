package no.nav.soknad.innsending.util.prefill

import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.util.Constants.ARENA_MAALGRUPPE
import no.nav.soknad.innsending.util.Constants.KONTORREGISTER_BORGER
import no.nav.soknad.innsending.util.Constants.PDL

object ServiceProperties {
	// Available external services and their corresponding properties that the frontend can request
	private val availableServicePropertiesMap = hashMapOf(
		PDL to listOf(
			"sokerIdentifikasjonsnummer",
			"sokerFornavn",
			"sokerEtternavn",
			"sokerAdresser",
			"sokerTelefonnummer",
			"sokerKjonn"
		),
		ARENA_MAALGRUPPE to listOf("sokerMaalgruppe"),
		KONTORREGISTER_BORGER to listOf("sokerKontonummer")
	)

	// Creates a hashmap with each external service to call with the corresponding properties to fetch from that service
	fun createServicePropertiesMap(
		inputList: List<String>,
	): Map<String, List<String>> {
		val resultMap = mutableMapOf<String, MutableList<String>>()

		inputList.forEach { inputString ->
			val key = findKeyForString(inputString, availableServicePropertiesMap)
			if (resultMap.containsKey(key)) {
				resultMap[key]?.add(inputString)
			} else {
				resultMap[key] = mutableListOf(inputString)
			}
		}

		return resultMap
	}

	fun findKeyForString(inputString: String, inputMap: Map<String, List<String>>): String {
		val matchingKey = inputMap.entries.find { inputString in it.value }?.key
		return matchingKey ?: throw IllegalActionException("'$inputString' not a valid property")
	}
}
