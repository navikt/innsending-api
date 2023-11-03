package no.nav.soknad.innsending.util.prefill

import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.util.Constants

object ServiceProperties {
	// Available external services and their corresponding properties that the frontend can request
	val availableServicePropertiesMap = hashMapOf(
		Constants.PDL to listOf("sokerFornavn", "sokerEtternavn"),
		Constants.ARENA to listOf("maalgruppe"),
	)

	// Creates a hashmap with each external service to call with the corresponding properties to fetch from that service
	fun createServicePropertiesMap(
		inputList: List<String>,
		serviceKeyMap: Map<String, List<String>>
	): Map<String, List<String>> {
		val resultMap = mutableMapOf<String, MutableList<String>>()

		inputList.forEach { inputString ->
			val key = findKeyForString(inputString, serviceKeyMap)
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
