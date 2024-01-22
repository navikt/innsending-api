package no.nav.soknad.innsending.service

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import no.nav.soknad.innsending.consumerapis.arena.ArenaConsumerInterface
import no.nav.soknad.innsending.consumerapis.kontoregister.KontoregisterInterface
import no.nav.soknad.innsending.consumerapis.pdl.PdlInterface
import no.nav.soknad.innsending.consumerapis.pdl.transformers.AddressTransformer.transformAddresses
import no.nav.soknad.innsending.consumerapis.pdl.transformers.NameTransformer.transformName
import no.nav.soknad.innsending.consumerapis.pdl.transformers.PhoneNumberTransformer.transformPhoneNumbers
import no.nav.soknad.innsending.exceptions.NonCriticalException
import no.nav.soknad.innsending.model.Aktivitet
import no.nav.soknad.innsending.model.Maalgruppe
import no.nav.soknad.innsending.model.PrefillData
import no.nav.soknad.innsending.util.Constants.ARENA_AKTIVITETER
import no.nav.soknad.innsending.util.Constants.ARENA_MAALGRUPPER
import no.nav.soknad.innsending.util.Constants.KONTORREGISTER_BORGER
import no.nav.soknad.innsending.util.Constants.PDL
import no.nav.soknad.innsending.util.prefill.ServiceProperties.createServicePropertiesMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

// Gets data from external services which will be used in the Fyllut application to prefill values in the form-fields and to validate that the application is correct for the user
@Service
class PrefillService(
	private val arenaConsumer: ArenaConsumerInterface,
	private val pdlApi: PdlInterface,
	private val kontoregisterService: KontoregisterInterface
) {
	private val logger: Logger = LoggerFactory.getLogger(javaClass)


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
				KONTORREGISTER_BORGER -> requestList.add(async { getKontonummer() })
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
				sokerKjonn = obj.sokerKjonn ?: acc.sokerKjonn,
				sokerTelefonnummer = obj.sokerTelefonnummer ?: acc.sokerTelefonnummer,
				sokerKontonummer = obj.sokerKontonummer ?: acc.sokerKontonummer,
			)
		}
	}

	suspend fun getKontonummer(): PrefillData {
		logger.info("Henter kontonummer fra kontoregister")

		val kontonummer: String
		try {
			kontonummer = kontoregisterService.getKontonummer()
		} catch (exception: NonCriticalException) {
			return PrefillData()
		}

		logger.info("Hentet kontonummer fra kontoregister")
		return PrefillData(sokerKontonummer = kontonummer)
	}

	suspend fun getPDLData(userId: String, properties: List<String>): PrefillData {
		logger.info("Henter data fra PDL")

		val personInfo = pdlApi.getPrefillPersonInfo(userId)
		val name = transformName(personInfo?.hentPerson?.navn)
		val addresses = transformAddresses(
			adressebeskyttelser = personInfo?.hentPerson?.adressebeskyttelse,
			bostedsAdresser = personInfo?.hentPerson?.bostedsadresse,
			kontaktadresser = personInfo?.hentPerson?.kontaktadresse,
			oppholdsadresser = personInfo?.hentPerson?.oppholdsadresse
		)
		val phoneNumber = transformPhoneNumbers(personInfo?.hentPerson?.telefonnummer)
		val gender = personInfo?.hentPerson?.kjoenn?.firstOrNull()?.kjoenn?.name

		logger.info("Hentet data fra PDL")

		return PrefillData(
			sokerFornavn = if (properties.contains("sokerFornavn")) name?.fornavn else null,
			sokerEtternavn = if (properties.contains("sokerEtternavn")) name?.etternavn else null,
			sokerAdresser = if (properties.contains("sokerAdresser")) addresses else null,
			sokerTelefonnummer = if (properties.contains("sokerTelefonnummer")) phoneNumber else null,
			sokerKjonn = if (properties.contains("sokerKjonn")) gender else null
		)
	}

	suspend fun getArenaMaalgrupper(userId: String, properties: List<String>): PrefillData {
		logger.info("Henter målgrupper fra Arena")

		val maalgrupper: List<Maalgruppe>
		try {
			maalgrupper = arenaConsumer.getMaalgrupper()
			if (maalgrupper.isEmpty()) return PrefillData()
		} catch (exception: NonCriticalException) {
			return PrefillData()
		}

		logger.info("Hentet målgrupper fra Arena")

		return PrefillData(
			sokerMaalgrupper = if (properties.contains("sokerMaalgrupper")) maalgrupper else null,
		)
	}

	suspend fun getArenaAktiviteter(userId: String, properties: List<String>): PrefillData {
		logger.info("Henter aktiviteter fra Arena")

		val aktiviteter: List<Aktivitet>

		try {
			aktiviteter = arenaConsumer.getAktiviteter()
			if (aktiviteter.isEmpty()) return PrefillData()
		} catch (arenaException: NonCriticalException) {
			return PrefillData()
		}

		logger.info("Hentet aktiviteter fra Arena")

		return PrefillData(
			sokerAktiviteter = if (properties.contains("sokerAktiviteter")) aktiviteter else null,
		)
	}

}
