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
			"${bostedsadresse.vegadresse?.adressenavn} ${bostedsadresse.vegadresse?.husnummer}${bostedsadresse.vegadresse?.husbokstav}"
		val foreignAddress = "${bostedsadresse.utenlandskAdresse?.adressenavnNummer}"

		return Adresse(
			gyldigFraOgMed = if (bostedsadresse.gyldigFraOgMed != null) LocalDate.parse(
				bostedsadresse.gyldigFraOgMed,
				DateTimeFormatter.ISO_LOCAL_DATE_TIME
			) else null,
			gyldigTilOgMed = if (bostedsadresse.gyldigTilOgMed != null) LocalDate.parse(
				bostedsadresse.gyldigTilOgMed,
				DateTimeFormatter.ISO_LOCAL_DATE_TIME
			) else null,
			co = bostedsadresse.coAdressenavn,
			adresse = if (isNorwegianAddress) norwegianAddress else foreignAddress,
			postnummer = bostedsadresse.vegadresse?.postnummer ?: bostedsadresse.utenlandskAdresse?.postkode,
			bygning = bostedsadresse.utenlandskAdresse?.bygningEtasjeLeilighet,
			postboks = bostedsadresse.utenlandskAdresse?.postboksNummerNavn,
			bySted = bostedsadresse.utenlandskAdresse?.bySted,
			region = bostedsadresse.utenlandskAdresse?.regionDistriktOmraade,
			landkode = if (isNorwegianAddress) "NOR" else bostedsadresse.utenlandskAdresse?.landkode,
		)
	}

	fun mapOppholdsadresse(oppholdsadresse: Oppholdsadresse): Adresse {
		val isNorwegianAddress = oppholdsadresse.vegadresse != null

		val norwegianAddress =
			"${oppholdsadresse.vegadresse?.adressenavn} ${oppholdsadresse.vegadresse?.husnummer}${oppholdsadresse.vegadresse?.husbokstav}"
		val foreignAddress = "${oppholdsadresse.utenlandskAdresse?.adressenavnNummer}"

		return Adresse(
			gyldigFraOgMed = if (oppholdsadresse.gyldigFraOgMed != null) LocalDate.parse(
				oppholdsadresse.gyldigFraOgMed,
				DateTimeFormatter.ISO_LOCAL_DATE_TIME
			) else null,
			gyldigTilOgMed = if (oppholdsadresse.gyldigTilOgMed != null) LocalDate.parse(
				oppholdsadresse.gyldigTilOgMed,
				DateTimeFormatter.ISO_LOCAL_DATE_TIME
			) else null,
			co = oppholdsadresse.coAdressenavn,
			adresse = if (isNorwegianAddress) norwegianAddress else foreignAddress,
			postnummer = oppholdsadresse.vegadresse?.postnummer ?: oppholdsadresse.utenlandskAdresse?.postkode,
			bygning = oppholdsadresse.utenlandskAdresse?.bygningEtasjeLeilighet,
			postboks = oppholdsadresse.utenlandskAdresse?.postboksNummerNavn,
			bySted = oppholdsadresse.utenlandskAdresse?.bySted,
			region = oppholdsadresse.utenlandskAdresse?.regionDistriktOmraade,
			landkode = if (isNorwegianAddress) "NOR" else oppholdsadresse.utenlandskAdresse?.landkode,
		)
	}

	fun mapKontaktadresse(kontaktadresse: Kontaktadresse): Adresse {
		val isNorwegianAddress = kontaktadresse.type == KontaktadresseType.INNLAND

		val norwegianAddress =
			"${kontaktadresse.vegadresse?.adressenavn} ${kontaktadresse.vegadresse?.husnummer}${kontaktadresse.vegadresse?.husbokstav}"
		val foreignAddress = "${kontaktadresse.utenlandskAdresse?.adressenavnNummer}"

		val postboks = "${kontaktadresse.postboksadresse?.postbokseier} ${kontaktadresse.postboksadresse?.postboks}"

		return Adresse(
			gyldigFraOgMed = if (kontaktadresse.gyldigFraOgMed != null) LocalDate.parse(
				kontaktadresse.gyldigFraOgMed,
				DateTimeFormatter.ISO_LOCAL_DATE_TIME
			) else null,
			gyldigTilOgMed = if (kontaktadresse.gyldigTilOgMed != null) LocalDate.parse(
				kontaktadresse.gyldigTilOgMed,
				DateTimeFormatter.ISO_LOCAL_DATE_TIME
			) else null,
			co = kontaktadresse.coAdressenavn,
			adresse = if (isNorwegianAddress) norwegianAddress else foreignAddress,
			postnummer = kontaktadresse.vegadresse?.postnummer ?: kontaktadresse.utenlandskAdresse?.postkode,
			bygning = kontaktadresse.utenlandskAdresse?.bygningEtasjeLeilighet,
			postboks = if (isNorwegianAddress) postboks else kontaktadresse.utenlandskAdresse?.postboksNummerNavn,
			bySted = kontaktadresse.utenlandskAdresse?.bySted,
			region = kontaktadresse.utenlandskAdresse?.regionDistriktOmraade,
			landkode = if (isNorwegianAddress) "NOR" else kontaktadresse.utenlandskAdresse?.landkode,
		)
	}


}
