package no.nav.soknad.innsending.utils.builders.pdl

import no.nav.soknad.innsending.pdl.generated.prefilldata.Bostedsadresse
import no.nav.soknad.innsending.pdl.generated.prefilldata.Metadata
import no.nav.soknad.innsending.pdl.generated.prefilldata.UtenlandskAdresse
import no.nav.soknad.innsending.pdl.generated.prefilldata.Vegadresse
import no.nav.soknad.innsending.utils.Date
import java.time.LocalDateTime

class BostedsadresseTestBuilder {
	private var angittFlyttedato: String? = Date.formatDate(LocalDateTime.now().minusDays(10))
	private var coAdressenavn: String? = "c/o John Doe"
	private var gyldigFraOgMed: String? = Date.formatDate(LocalDateTime.now().minusDays(10))
	private var gyldigTilOgMed: String? = Date.formatDate(LocalDateTime.now().plusYears(1))
	private var vegadresse: Vegadresse? = VegadresseTestBuilder().build()
	private var utenlandskAdresse: UtenlandskAdresse? = null
	private var metadata: Metadata = MetadataTestBuilder().build()

	fun angittFlyttedato(angittFlyttedato: String?) = apply { this.angittFlyttedato = angittFlyttedato }
	fun coAdressenavn(coAdressenavn: String?) = apply { this.coAdressenavn = coAdressenavn }
	fun gyldigFraOgMed(gyldigFraOgMed: String?) = apply { this.gyldigFraOgMed = gyldigFraOgMed }
	fun gyldigTilOgMed(gyldigTilOgMed: String?) = apply { this.gyldigTilOgMed = gyldigTilOgMed }
	fun vegadresse(vegadresse: Vegadresse?) = apply { this.vegadresse = vegadresse }
	fun utenlandskAdresse(utenlandskAdresse: UtenlandskAdresse?) =
		apply { this.utenlandskAdresse = utenlandskAdresse }

	fun metadata(metadata: Metadata) = apply { this.metadata = metadata }

	fun build() = Bostedsadresse(
		angittFlyttedato = angittFlyttedato,
		coAdressenavn = coAdressenavn,
		gyldigFraOgMed = gyldigFraOgMed,
		gyldigTilOgMed = gyldigTilOgMed,
		vegadresse = vegadresse,
		utenlandskAdresse = utenlandskAdresse,
		metadata = metadata
	)
}
