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
import no.nav.soknad.innsending.util.prefill.ServiceProperties.createServicePropertiesMap
import org.springframework.stereotype.Service

@Service
class PrefillService(
	private val arenaConsumer: ArenaConsumerInterface,
	private val pdlApi: PdlInterface
) {

	fun getPrefillData(properties: List<String>, userId: String): PrefillData = runBlocking {
		// Create a new hashmap of which services to call based on the input properties
		val servicePropertiesMap = createServicePropertiesMap(properties)

		// Create a list of requests to external services based on the servicePropertiesMap
		val requestList = mutableListOf<Deferred<PrefillData>>()

		servicePropertiesMap.forEach { (service, properties) ->
			when (service) {
				PDL -> requestList.add(async { getPDLData(userId, properties) })
				ARENA -> requestList.add(async { getArenaData(userId, properties) })
			}
		}

		// Execute requests in parallell
		val results = requestList.awaitAll()

		// Combine results from external services into one object
		combineResults(results)
	}

	fun combineResults(results: List<PrefillData>): PrefillData {
		return results.fold(PrefillData()) { acc, obj ->
			PrefillData(
				sokerFornavn = obj.sokerFornavn ?: acc.sokerFornavn,
				sokerEtternavn = obj.sokerEtternavn ?: acc.sokerEtternavn,
				sokerMaalgrupper = obj.sokerMaalgrupper ?: acc.sokerMaalgrupper
			)
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
		val maalgrupper = arenaConsumer.getMaalgrupper()
		if (maalgrupper.isEmpty()) return PrefillData()

		return PrefillData(
			sokerMaalgrupper = if (properties.contains("sokerMaalgrupper")) maalgrupper else null,
		)
	}

}
