package no.nav.soknad.innsending.consumerapis.pdl.mapper

import no.nav.soknad.innsending.model.Adresse
import no.nav.soknad.innsending.pdl.generated.enums.KontaktadresseType
import no.nav.soknad.innsending.pdl.generated.prefilldata.Bostedsadresse
import no.nav.soknad.innsending.pdl.generated.prefilldata.Kontaktadresse
import no.nav.soknad.innsending.pdl.generated.prefilldata.Oppholdsadresse
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class Adressemapper {
	fun mapBostedsadresse(bostedsadresse: Bostedsadresse): Adresse {
		val isNorwegianAddress = bostedsadresse.vegadresse != null

		val norwegianAddress =
			nullIfEmptyString("${bostedsadresse.vegadresse?.adressenavn ?: ""} ${bostedsadresse.vegadresse?.husnummer ?: ""}${bostedsadresse.vegadresse?.husbokstav ?: ""}")
		val foreignAddress = nullIfEmptyString(bostedsadresse.utenlandskAdresse?.adressenavnNummer)

		return Adresse(
			gyldigFraOgMed = if (bostedsadresse.gyldigFraOgMed != null) LocalDate.parse(
				bostedsadresse.gyldigFraOgMed,
				DateTimeFormatter.ISO_LOCAL_DATE_TIME
			) else null,
			gyldigTilOgMed = if (bostedsadresse.gyldigTilOgMed != null) LocalDate.parse(
				bostedsadresse.gyldigTilOgMed,
				DateTimeFormatter.ISO_LOCAL_DATE_TIME
			) else null,
			co = nullIfEmptyString(bostedsadresse.coAdressenavn),
			adresse = if (isNorwegianAddress) norwegianAddress else foreignAddress,
			postnummer = nullIfEmptyString(
				bostedsadresse.vegadresse?.postnummer ?: bostedsadresse.utenlandskAdresse?.postkode
			),
			bygning = nullIfEmptyString(bostedsadresse.utenlandskAdresse?.bygningEtasjeLeilighet),
			postboks = nullIfEmptyString(bostedsadresse.utenlandskAdresse?.postboksNummerNavn),
			bySted = nullIfEmptyString(bostedsadresse.utenlandskAdresse?.bySted),
			region = nullIfEmptyString(bostedsadresse.utenlandskAdresse?.regionDistriktOmraade),
			landkode = if (isNorwegianAddress) "NOR" else nullIfEmptyString(bostedsadresse.utenlandskAdresse?.landkode),
		)
	}

	fun mapOppholdsadresse(oppholdsadresse: Oppholdsadresse): Adresse {
		val isNorwegianAddress = oppholdsadresse.vegadresse != null

		val norwegianAddress =
			nullIfEmptyString("${oppholdsadresse.vegadresse?.adressenavn ?: ""} ${oppholdsadresse.vegadresse?.husnummer ?: ""}${oppholdsadresse.vegadresse?.husbokstav ?: ""}")
		val foreignAddress = nullIfEmptyString(oppholdsadresse.utenlandskAdresse?.adressenavnNummer)

		return Adresse(
			gyldigFraOgMed = if (oppholdsadresse.gyldigFraOgMed != null) LocalDate.parse(
				oppholdsadresse.gyldigFraOgMed,
				DateTimeFormatter.ISO_LOCAL_DATE_TIME
			) else null,
			gyldigTilOgMed = if (oppholdsadresse.gyldigTilOgMed != null) LocalDate.parse(
				oppholdsadresse.gyldigTilOgMed,
				DateTimeFormatter.ISO_LOCAL_DATE_TIME
			) else null,
			co = nullIfEmptyString(oppholdsadresse.coAdressenavn),
			adresse = if (isNorwegianAddress) norwegianAddress else foreignAddress,
			postnummer = nullIfEmptyString(
				oppholdsadresse.vegadresse?.postnummer ?: oppholdsadresse.utenlandskAdresse?.postkode
			),
			bygning = nullIfEmptyString(oppholdsadresse.utenlandskAdresse?.bygningEtasjeLeilighet),
			postboks = nullIfEmptyString(oppholdsadresse.utenlandskAdresse?.postboksNummerNavn),
			bySted = nullIfEmptyString(oppholdsadresse.utenlandskAdresse?.bySted),
			region = nullIfEmptyString(oppholdsadresse.utenlandskAdresse?.regionDistriktOmraade),
			landkode = if (isNorwegianAddress) "NOR" else nullIfEmptyString(oppholdsadresse.utenlandskAdresse?.landkode),
		)
	}

	fun mapKontaktadresse(kontaktadresse: Kontaktadresse): Adresse {
		val isNorwegianAddress = kontaktadresse.type == KontaktadresseType.INNLAND

		val norwegianAddress =
			nullIfEmptyString("${kontaktadresse.vegadresse?.adressenavn ?: ""} ${kontaktadresse.vegadresse?.husnummer ?: ""}${kontaktadresse.vegadresse?.husbokstav ?: ""}")
		val foreignAddress = nullIfEmptyString(kontaktadresse.utenlandskAdresse?.adressenavnNummer)

		val postboks =
			nullIfEmptyString("${kontaktadresse.postboksadresse?.postbokseier ?: ""} ${kontaktadresse.postboksadresse?.postboks ?: ""}")

		return Adresse(
			gyldigFraOgMed = if (kontaktadresse.gyldigFraOgMed != null) LocalDate.parse(
				kontaktadresse.gyldigFraOgMed,
				DateTimeFormatter.ISO_LOCAL_DATE_TIME
			) else null,
			gyldigTilOgMed = if (kontaktadresse.gyldigTilOgMed != null) LocalDate.parse(
				kontaktadresse.gyldigTilOgMed,
				DateTimeFormatter.ISO_LOCAL_DATE_TIME
			) else null,
			co = nullIfEmptyString(kontaktadresse.coAdressenavn),
			adresse = if (isNorwegianAddress) norwegianAddress else foreignAddress,
			postnummer = nullIfEmptyString(
				kontaktadresse.vegadresse?.postnummer ?: kontaktadresse.utenlandskAdresse?.postkode
			),
			bygning = nullIfEmptyString(kontaktadresse.utenlandskAdresse?.bygningEtasjeLeilighet),
			postboks = if (isNorwegianAddress) postboks else kontaktadresse.utenlandskAdresse?.postboksNummerNavn,
			bySted = nullIfEmptyString(kontaktadresse.utenlandskAdresse?.bySted),
			region = nullIfEmptyString(kontaktadresse.utenlandskAdresse?.regionDistriktOmraade),
			landkode = if (isNorwegianAddress) "NOR" else nullIfEmptyString(kontaktadresse.utenlandskAdresse?.landkode),
		)
	}

	private fun nullIfEmptyString(value: String?): String? {
		val trimmedValue = value?.trim()
		return if (!trimmedValue.isNullOrBlank()) trimmedValue else null
	}


}
