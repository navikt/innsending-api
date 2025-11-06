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
import no.nav.soknad.innsending.model.Adresse
import no.nav.soknad.innsending.model.Maalgruppe
import no.nav.soknad.innsending.model.PrefillData
import no.nav.soknad.innsending.pdl.generated.prefilldata.Folkeregisteridentifikator
import no.nav.soknad.innsending.util.Constants.ARENA_MAALGRUPPE
import no.nav.soknad.innsending.util.Constants.KONTORREGISTER_BORGER
import no.nav.soknad.innsending.util.Constants.PDL
import no.nav.soknad.innsending.util.prefill.MaalgruppeUtils
import no.nav.soknad.innsending.util.prefill.ServiceProperties.createServicePropertiesMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.Marker
import org.slf4j.MarkerFactory
import org.springframework.stereotype.Service

// Gets data from external services which will be used in the Fyllut application to prefill values in the form-fields and to validate that the application is correct for the user
@Service
class PrefillService(
	private val arenaConsumer: ArenaConsumerInterface,
	private val pdlApi: PdlInterface,
	private val kontoregisterService: KontoregisterInterface,
	private val kodeverkService: KodeverkService
) {
	private val logger: Logger = LoggerFactory.getLogger(javaClass)
	private val secureLogger = LoggerFactory.getLogger("secureLogger")
	private val secureLogsMarker: Marker = MarkerFactory.getMarker("TEAM_LOGS")


	fun getPrefillData(properties: List<String>, userId: String): PrefillData = runBlocking {
		// Create a new hashmap of which services to call based on the input properties
		val servicePropertiesMap = createServicePropertiesMap(properties)

		// Create a list of requests to external services based on the servicePropertiesMap
		val requestList = mutableListOf<Deferred<PrefillData>>()

		servicePropertiesMap.forEach { (service, properties) ->
			when (service) {
				PDL -> requestList.add(async { getPDLData(userId, properties) })
				ARENA_MAALGRUPPE -> requestList.add(async { getArenaMaalgruppe(userId, properties) })
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
				sokerIdentifikasjonsnummer = obj.sokerIdentifikasjonsnummer ?: acc.sokerIdentifikasjonsnummer,
				sokerFornavn = obj.sokerFornavn ?: acc.sokerFornavn,
				sokerEtternavn = obj.sokerEtternavn ?: acc.sokerEtternavn,
				sokerMaalgruppe = obj.sokerMaalgruppe ?: acc.sokerMaalgruppe,
				sokerAdresser = obj.sokerAdresser ?: acc.sokerAdresser,
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
		val identifikasjonsnummer = resolveIdentifikasjonsnummer(personInfo?.hentPerson?.folkeregisteridentifikator, userId)
		val name = transformName(personInfo?.hentPerson?.navn)
		val addresses = transformAddresses(
			adressebeskyttelser = personInfo?.hentPerson?.adressebeskyttelse,
			bostedsAdresser = personInfo?.hentPerson?.bostedsadresse,
			kontaktadresser = personInfo?.hentPerson?.kontaktadresse,
			oppholdsadresser = personInfo?.hentPerson?.oppholdsadresse
		).run {
			copy(
				bostedsadresse = bostedsadresse?.let(::enrichAddress),
				kontaktadresser = kontaktadresser?.map(::enrichAddress),
				oppholdsadresser = oppholdsadresser?.map(::enrichAddress)
			)
		}
		val phoneNumber = transformPhoneNumbers(personInfo?.hentPerson?.telefonnummer)

		logger.info("Hentet data fra PDL")

		return PrefillData(
			sokerIdentifikasjonsnummer = if (properties.contains("sokerIdentifikasjonsnummer")) identifikasjonsnummer else null,
			sokerFornavn = if (properties.contains("sokerFornavn")) name?.fornavn else null,
			sokerEtternavn = if (properties.contains("sokerEtternavn")) name?.etternavn else null,
			sokerAdresser = if (properties.contains("sokerAdresser")) addresses else null,
			sokerTelefonnummer = if (properties.contains("sokerTelefonnummer")) phoneNumber else null,
		)
	}

	private fun resolveIdentifikasjonsnummer(
		pdlIdents: List<Folkeregisteridentifikator>?, userId: String
	): String {
		val userIdMatch = pdlIdents?.firstOrNull { it.identifikasjonsnummer == userId }?.identifikasjonsnummer
		if (userIdMatch == null) {
			secureLogger.warn("Mismatch while resolving identifikasjonsnummer: token=$userId, PDL=$userIdMatch (PDL response $pdlIdents)")
			logger.warn(secureLogsMarker, "Mismatch while resolving identifikasjonsnummer: token=$userId, PDL=$userIdMatch")
		}
		return userIdMatch ?: pdlIdents?.firstOrNull { !it.metadata.historisk }?.identifikasjonsnummer ?: userId
	}

	private fun enrichAddress(pdlAddress: Adresse): Adresse {
		val norwegianAddressWithPoststed = pdlAddress
			.takeIf { it.landkode == "NOR" && it.postnummer?.isNotEmpty() == true }
			?.run { copy(bySted = kodeverkService.getPoststed(postnummer!!)) }
		return norwegianAddressWithPoststed ?: pdlAddress
	}

	suspend fun getArenaMaalgruppe(userId: String, properties: List<String>): PrefillData {
		logger.info("Henter målgrupper fra Arena")

		val maalgrupper: List<Maalgruppe>
		try {
			maalgrupper = arenaConsumer.getMaalgrupper()
			if (maalgrupper.isEmpty()) return PrefillData()
		} catch (exception: Exception) {
			return PrefillData()
		}

		logger.info("Hentet målgrupper fra Arena")

		val prioritizedMaalgruppe = MaalgruppeUtils.getPrioritzedMaalgruppe(maalgrupper)

		return PrefillData(
			sokerMaalgruppe = if (properties.contains("sokerMaalgruppe")) prioritizedMaalgruppe else null,
		)
	}

}
