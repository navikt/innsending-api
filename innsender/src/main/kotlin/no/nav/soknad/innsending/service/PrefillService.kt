package no.nav.soknad.innsending.service

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import no.nav.soknad.innsending.consumerapis.pdl.PdlInterface
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.PrefilledData
import no.nav.soknad.innsending.util.Constants.ARENA
import no.nav.soknad.innsending.util.Constants.PDL
import no.nav.soknad.innsending.util.extensions.ifContains
import org.springframework.stereotype.Service

@Service
class PrefillService(private val pdlApi: PdlInterface) {

	// Available external services and their corresponding properties that the frontend can request
	private val availableServicePropertiesMap = hashMapOf(
		PDL to listOf("sokerFornavn", "sokerEtternavn"),
		ARENA to listOf("maalgruppe"),
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

	fun getPrefillData(properties: List<String>, userId: String): PrefilledData {
		// Create a new hashmap of which services to call based on the input properties
		val servicePropertiesMap = createServicePropertiesMap(properties, availableServicePropertiesMap)

		return runBlocking {
			val requestList = mutableListOf<Deferred<PrefilledData>>()

			// Create a list of requests to external services based on the servicePropertiesMap
			servicePropertiesMap.forEach { (service, properties) ->
				when (service) {
					PDL -> requestList.add(async { getPDLData(userId, properties) })
					ARENA -> requestList.add(async { getArenaData(userId, properties) })
				}
			}

			// Execute requests in parallell
			val results = awaitAll(*requestList.toTypedArray())

			// Combine results from external services into one object
			val combinedObject = results.fold(PrefilledData()) { acc, obj ->
				PrefilledData(
					sokerFornavn = obj.sokerFornavn ?: acc.sokerFornavn,
					sokerEtternavn = obj.sokerEtternavn ?: acc.sokerEtternavn,
				)
			}

			combinedObject
		}
	}

	suspend fun getPDLData(userId: String, properties: List<String>): PrefilledData {
		val personInfo = pdlApi.getPrefillPersonInfo(userId)
		val navn = personInfo?.hentPerson?.navn?.first()
		return PrefilledData(
			sokerFornavn = properties.ifContains("sokerFornavn", navn?.fornavn),
			sokerEtternavn = properties.ifContains("sokerEtternavn", navn?.etternavn)
		)
	}

	suspend fun getArenaData(userId: String, properties: List<String>): PrefilledData {
		return PrefilledData()
	}


}
