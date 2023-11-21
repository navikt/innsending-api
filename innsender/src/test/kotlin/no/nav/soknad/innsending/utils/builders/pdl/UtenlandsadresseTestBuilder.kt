package no.nav.soknad.innsending.utils.builders.pdl

import no.nav.soknad.innsending.pdl.generated.prefilldata.UtenlandskAdresse

class UtenlandsadresseTestBuilder {
	private var adressenavnNummer: String? = "Calle de Madrid 24"
	private var bygningEtasjeLeilighet: String? = "23"
	private var postboksNummerNavn: String? = "PO Box 456"
	private var postkode: String? = "78901"
	private var bySted: String? = "Madrid"
	private var regionDistriktOmraade: String? = "Madrid"
	private var landkode: String = "ESP"

	fun adressenavnNummer(adressenavnNummer: String?) = apply { this.adressenavnNummer = adressenavnNummer }
	fun bygningEtasjeLeilighet(bygningEtasjeLeilighet: String?) =
		apply { this.bygningEtasjeLeilighet = bygningEtasjeLeilighet }

	fun postboksNummerNavn(postboksNummerNavn: String?) = apply { this.postboksNummerNavn = postboksNummerNavn }
	fun postkode(postkode: String?) = apply { this.postkode = postkode }
	fun bySted(bySted: String?) = apply { this.bySted = bySted }
	fun regionDistriktOmraade(regionDistriktOmraade: String?) =
		apply { this.regionDistriktOmraade = regionDistriktOmraade }

	fun landkode(landkode: String) = apply { this.landkode = landkode }

	fun build() = UtenlandskAdresse(
		adressenavnNummer = adressenavnNummer,
		bygningEtasjeLeilighet = bygningEtasjeLeilighet,
		postboksNummerNavn = postboksNummerNavn,
		postkode = postkode,
		bySted = bySted,
		regionDistriktOmraade = regionDistriktOmraade,
		landkode = landkode
	)
}
