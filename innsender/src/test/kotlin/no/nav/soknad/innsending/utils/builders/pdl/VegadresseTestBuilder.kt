package no.nav.soknad.innsending.utils.builders.pdl

import no.nav.soknad.innsending.pdl.generated.prefilldata.Vegadresse

class VegadresseTestBuilder {
	private var husbokstav: String? = "C"
	private var husnummer: String? = "24"
	private var adressenavn: String? = "Klostergata"
	private var bruksenhetsnummer: String? = "H0201"
	private var tilleggsnavn: String? = null
	private var postnummer: String? = "0585"

	fun husbokstav(husbokstav: String?) = apply { this.husbokstav = husbokstav }
	fun husnummer(husnummer: String?) = apply { this.husnummer = husnummer }
	fun adressenavn(adressenavn: String?) = apply { this.adressenavn = adressenavn }
	fun bruksenhetsnummer(bruksenhetsnummer: String?) = apply { this.bruksenhetsnummer = bruksenhetsnummer }
	fun tilleggsnavn(tilleggsnavn: String?) = apply { this.tilleggsnavn = tilleggsnavn }
	fun postnummer(postnummer: String?) = apply { this.postnummer = postnummer }

	fun build() = Vegadresse(
		husbokstav = husbokstav,
		husnummer = husnummer,
		adressenavn = adressenavn,
		bruksenhetsnummer = bruksenhetsnummer,
		tilleggsnavn = tilleggsnavn,
		postnummer = postnummer
	)
}
