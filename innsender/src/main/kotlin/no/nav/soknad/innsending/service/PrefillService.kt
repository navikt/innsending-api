package no.nav.soknad.innsending.service

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import no.nav.soknad.innsending.consumerapis.arena.ArenaConsumerInterface
import no.nav.soknad.innsending.consumerapis.pdl.PdlInterface
import no.nav.soknad.innsending.model.PrefillData
import no.nav.soknad.innsending.util.Constants.ARENA
import no.nav.soknad.innsending.util.Constants.PDL
import no.nav.soknad.innsending.util.extensions.ifContains
import no.nav.soknad.innsending.util.prefill.ServiceProperties.availableServicePropertiesMap
import no.nav.soknad.innsending.util.prefill.ServiceProperties.createServicePropertiesMap
import org.springframework.stereotype.Service

@Service
class PrefillService(
	private val arenaConsumer: ArenaConsumerInterface,
	private val pdlApi: PdlInterface
) {

	fun getPrefillData(properties: List<String>, userId: String): PrefillData {
		// Create a new hashmap of which services to call based on the input properties
		val servicePropertiesMap = createServicePropertiesMap(properties, availableServicePropertiesMap)

		return runBlocking {
			val requestList = mutableListOf<Deferred<PrefillData>>()

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
			val combinedObject = results.fold(PrefillData()) { acc, obj ->
				PrefillData(
					sokerFornavn = obj.sokerFornavn ?: acc.sokerFornavn,
					sokerEtternavn = obj.sokerEtternavn ?: acc.sokerEtternavn,
					sokerMaalgruppetype = obj.sokerMaalgruppetype ?: acc.sokerMaalgruppetype
				)
			}

			combinedObject
		}
	}

	suspend fun getPDLData(userId: String, properties: List<String>): PrefillData {
		val personInfo = pdlApi.getPrefillPersonInfo(userId)
		val navn = personInfo?.hentPerson?.navn?.first()
		return PrefillData(
			sokerFornavn = properties.ifContains("sokerFornavn", navn?.fornavn),
			sokerEtternavn = properties.ifContains("sokerEtternavn", navn?.etternavn)
		)
	}

	suspend fun getArenaData(userId: String, properties: List<String>): PrefillData {
		val maalgruppe = arenaConsumer.getMaalgruppe() //FIXME: Check date here
		if (maalgruppe.isEmpty()) return PrefillData()

		return PrefillData(
			sokerMaalgruppetype = properties.ifContains("sokerMaalgruppetype", maalgruppe.first().maalgruppetype.name),
		)
	}


}
