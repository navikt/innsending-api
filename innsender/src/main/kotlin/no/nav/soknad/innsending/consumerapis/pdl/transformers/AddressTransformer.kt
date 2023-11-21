package no.nav.soknad.innsending.consumerapis.pdl.transformers

import no.nav.soknad.innsending.consumerapis.pdl.mapper.Adressemapper
import no.nav.soknad.innsending.model.Adresse
import no.nav.soknad.innsending.model.Adresser
import no.nav.soknad.innsending.pdl.generated.prefilldata.Bostedsadresse
import no.nav.soknad.innsending.pdl.generated.prefilldata.Kontaktadresse
import no.nav.soknad.innsending.pdl.generated.prefilldata.Oppholdsadresse
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Would like to use an interface and more generic functions here, but since the classes are generated, it's not possible
object AddressTransformer {
	fun transformAddresses(
		bostedsAdresser: List<Bostedsadresse>?,
		oppholdsadresser: List<Oppholdsadresse>?,
		kontaktadresser: List<Kontaktadresse>?,
	): Adresser {
		return Adresser(
			bostedsadresse = transformBostedsAdresser(bostedsAdresser),
			oppholdsadresser = transformOppholdsadresser(oppholdsadresser),
			kontaktadresser = transformKontakadresser(kontaktadresser),
		)
	}

	// Gets the current bostedsAdresse. There is always only one (either in Norway or abroad)
	private fun transformBostedsAdresser(bostedsAdresser: List<Bostedsadresse>?): Adresse? {
		val currentBostedsadresse = findCurrentBostedsAdresse(bostedsAdresser)
		val mapper = Adressemapper()
		return currentBostedsadresse?.let { mapper.mapBostedsadresse(it) }
	}

	// Gets one oppholdsAddresse in Norway and one abroad (if they exist)
	private fun transformOppholdsadresser(oppholdsadresser: List<Oppholdsadresse>?): List<Adresse> {
		val oppholdsAdresseNorge =
			findCurrentOppholdsadresse(oppholdsadresser?.filter { it.utenlandskAdresse == null && it.vegadresse != null })
		val oppholdsAdresseUtland =
			findCurrentOppholdsadresse(oppholdsadresser?.filter { it.utenlandskAdresse != null && it.vegadresse == null })

		val mapper = Adressemapper()
		val oppholdsAdresseNorgeMapped = oppholdsAdresseNorge?.let { mapper.mapOppholdsadresse(it) }
		val oppholdsAdresseUtlandMapped = oppholdsAdresseUtland?.let { mapper.mapOppholdsadresse(it) }

		return listOfNotNull(oppholdsAdresseNorgeMapped, oppholdsAdresseUtlandMapped)
	}

	// Gets all relevant kontaktAdresser
	private fun transformKontakadresser(kontaktadresser: List<Kontaktadresse>?): List<Adresse> {
		val mapper = Adressemapper()
		return findCurrentKontaktadresser(kontaktadresser).map { mapper.mapKontaktadresse(it) }
	}

	private fun findCurrentBostedsAdresse(bostedsAdresser: List<Bostedsadresse>?): Bostedsadresse? {
		if (bostedsAdresser.isNullOrEmpty()) return null
		val today = LocalDate.now()

		return bostedsAdresser
			.filter {
				if (it.gyldigTilOgMed != null)
					LocalDate.parse(it.gyldigTilOgMed, DateTimeFormatter.ISO_LOCAL_DATE_TIME) >= today
				else true
			}
			.filter {
				if (it.gyldigFraOgMed != null)
					LocalDate.parse(it.gyldigFraOgMed, DateTimeFormatter.ISO_LOCAL_DATE_TIME) <= today
				else true
			}
			.maxByOrNull { address -> LocalDate.parse(address.gyldigFraOgMed, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
			?: bostedsAdresser.firstOrNull { it.gyldigFraOgMed == null }
	}

	private fun findCurrentOppholdsadresse(oppholdsadresser: List<Oppholdsadresse>?): Oppholdsadresse? {
		if (oppholdsadresser.isNullOrEmpty()) return null
		val today = LocalDate.now()

		return oppholdsadresser
			.filter {
				if (it.gyldigTilOgMed != null)
					LocalDate.parse(it.gyldigTilOgMed, DateTimeFormatter.ISO_LOCAL_DATE_TIME) >= today
				else true
			}
			.filter {
				if (it.gyldigFraOgMed != null)
					LocalDate.parse(it.gyldigFraOgMed, DateTimeFormatter.ISO_LOCAL_DATE_TIME) <= today
				else true
			}
			.maxByOrNull { address -> LocalDate.parse(address.gyldigFraOgMed, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
			?: oppholdsadresser.firstOrNull { it.gyldigFraOgMed == null }
	}

	private fun findCurrentKontaktadresser(kontaktadresser: List<Kontaktadresse>?): List<Kontaktadresse> {
		if (kontaktadresser.isNullOrEmpty()) return emptyList()
		val today = LocalDate.now()

		return kontaktadresser
			.filter {
				if (it.gyldigTilOgMed != null)
					LocalDate.parse(it.gyldigTilOgMed, DateTimeFormatter.ISO_LOCAL_DATE_TIME) >= today
				else true
			}
			.filter {
				if (it.gyldigFraOgMed != null)
					LocalDate.parse(it.gyldigFraOgMed, DateTimeFormatter.ISO_LOCAL_DATE_TIME) <= today
				else true
			}
	}
}
