package no.nav.soknad.innsending.utils.builders.pdl

import no.nav.soknad.innsending.pdl.generated.prefilldata.Metadata
import no.nav.soknad.innsending.pdl.generated.prefilldata.Oppholdsadresse
import no.nav.soknad.innsending.pdl.generated.prefilldata.UtenlandskAdresse
import no.nav.soknad.innsending.pdl.generated.prefilldata.Vegadresse
import no.nav.soknad.innsending.utils.Date
import java.time.LocalDateTime

class OppholdsadresseTestBuilder {
	private var oppholdAnnetSted: String? = "MILITAER"
	private var coAdressenavn: String? = "c/o John Doe"
	private var gyldigFraOgMed: String? = Date.formatDate(LocalDateTime.now().minusDays(10))
	private var gyldigTilOgMed: String? = Date.formatDate(LocalDateTime.now().plusYears(1))
	private var vegadresse: Vegadresse? = VegadresseTestBuilder().build()
	private var utenlandskAdresse: UtenlandskAdresse? = null
	private var metadata: Metadata = MetadataTestBuilder().build()

	fun oppholdAnnetSted(oppholdAnnetSted: String?) = apply { this.oppholdAnnetSted = oppholdAnnetSted }
	fun coAdressenavn(coAdressenavn: String?) = apply { this.coAdressenavn = coAdressenavn }
	fun gyldigFraOgMed(gyldigFraOgMed: String?) = apply { this.gyldigFraOgMed = gyldigFraOgMed }
	fun gyldigTilOgMed(gyldigTilOgMed: String?) = apply { this.gyldigTilOgMed = gyldigTilOgMed }
	fun utenlandskAdresse(utenlandskAdresse: UtenlandskAdresse?) = apply { this.utenlandskAdresse = utenlandskAdresse }
	fun vegadresse(vegadresse: Vegadresse?) = apply { this.vegadresse = vegadresse }
	fun metadata(metadata: Metadata) = apply { this.metadata = metadata }

	fun build() = Oppholdsadresse(
		oppholdAnnetSted = oppholdAnnetSted,
		coAdressenavn = coAdressenavn,
		gyldigFraOgMed = gyldigFraOgMed,
		gyldigTilOgMed = gyldigTilOgMed,
		utenlandskAdresse = utenlandskAdresse,
		vegadresse = vegadresse,
		metadata = metadata
	)
}
