package no.nav.soknad.innsending.service

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import no.nav.soknad.innsending.consumerapis.arena.ArenaConsumerInterface
import no.nav.soknad.innsending.consumerapis.arena.ArenaException
import no.nav.soknad.innsending.consumerapis.pdl.PdlInterface
import no.nav.soknad.innsending.consumerapis.pdl.transformers.AddressTransformer.transformAddresses
import no.nav.soknad.innsending.consumerapis.pdl.transformers.NameTransformer.transformName
import no.nav.soknad.innsending.model.Aktivitet
import no.nav.soknad.innsending.model.Maalgruppe
import no.nav.soknad.innsending.model.PrefillData
import no.nav.soknad.innsending.util.Constants.ARENA_AKTIVITETER
import no.nav.soknad.innsending.util.Constants.ARENA_MAALGRUPPER
import no.nav.soknad.innsending.util.Constants.PDL
import no.nav.soknad.innsending.util.prefill.ServiceProperties.createServicePropertiesMap
import org.springframework.stereotype.Service

// Gets data from external services which will be used in the Fyllut application to prefill values in the form-fields and to validate that the application is correct for the user
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
				ARENA_MAALGRUPPER -> requestList.add(async { getArenaMaalgrupper(userId, properties) })
				ARENA_AKTIVITETER -> requestList.add(async { getArenaAktiviteter(userId, properties) })
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
				sokerMaalgrupper = obj.sokerMaalgrupper ?: acc.sokerMaalgrupper,
				sokerAktiviteter = obj.sokerAktiviteter ?: acc.sokerAktiviteter,
				sokerAdresser = obj.sokerAdresser ?: acc.sokerAdresser,
			)
		}
	}

	suspend fun getPDLData(userId: String, properties: List<String>): PrefillData {
		val personInfo = pdlApi.getPrefillPersonInfo(userId)
		val navn = transformName(personInfo?.hentPerson?.navn)
		val addresses = transformAddresses(
			adressebeskyttelser = personInfo?.hentPerson?.adressebeskyttelse,
			bostedsAdresser = personInfo?.hentPerson?.bostedsadresse,
			kontaktadresser = personInfo?.hentPerson?.kontaktadresse,
			oppholdsadresser = personInfo?.hentPerson?.oppholdsadresse
		)
		return PrefillData(
			sokerFornavn = if (properties.contains("sokerFornavn")) navn?.fornavn else null,
			sokerEtternavn = if (properties.contains("sokerEtternavn")) navn?.etternavn else null,
			sokerAdresser = if (properties.contains("sokerAdresser")) addresses else null,
		)
	}

	suspend fun getArenaMaalgrupper(userId: String, properties: List<String>): PrefillData {
		val maalgrupper: List<Maalgruppe>
		try {
			maalgrupper = arenaConsumer.getMaalgrupper()
			if (maalgrupper.isEmpty()) return PrefillData()
		} catch (arenaException: ArenaException) {
			return PrefillData()
		}

		return PrefillData(
			sokerMaalgrupper = if (properties.contains("sokerMaalgrupper")) maalgrupper else null,
		)
	}

	suspend fun getArenaAktiviteter(userId: String, properties: List<String>): PrefillData {
		val aktiviteter: List<Aktivitet>

		try {
			aktiviteter = arenaConsumer.getAktiviteter()
			if (aktiviteter.isEmpty()) return PrefillData()
		} catch (arenaException: ArenaException) {
			return PrefillData()
		}

		return PrefillData(
			sokerAktiviteter = if (properties.contains("sokerAktiviteter")) aktiviteter else null,
		)
	}

}
