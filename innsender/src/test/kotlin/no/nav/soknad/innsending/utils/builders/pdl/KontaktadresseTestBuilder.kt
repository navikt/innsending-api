package no.nav.soknad.innsending.utils.builders.pdl

import no.nav.soknad.innsending.pdl.generated.enums.KontaktadresseType
import no.nav.soknad.innsending.pdl.generated.prefilldata.*
import no.nav.soknad.innsending.utils.Date
import java.time.LocalDateTime

class KontaktadresseTestBuilder {
	private var gyldigFraOgMed: String? = Date.formatDate(LocalDateTime.now().minusDays(10))
	private var gyldigTilOgMed: String? = Date.formatDate(LocalDateTime.now().plusYears(1))
	private var type: KontaktadresseType = KontaktadresseType.INNLAND
	private var coAdressenavn: String? = "c/o John Doe"
	private var postboksadresse: Postboksadresse? = null
	private var vegadresse: Vegadresse? = VegadresseTestBuilder().build()
	private var utenlandskAdresse: UtenlandskAdresse? = null
	private var metadata: Metadata = MetadataTestBuilder().build()

	fun gyldigFraOgMed(gyldigFraOgMed: String?) = apply { this.gyldigFraOgMed = gyldigFraOgMed }
	fun gyldigTilOgMed(gyldigTilOgMed: String?) = apply { this.gyldigTilOgMed = gyldigTilOgMed }
	fun type(type: KontaktadresseType) = apply { this.type = type }
	fun coAdressenavn(coAdressenavn: String?) = apply { this.coAdressenavn = coAdressenavn }
	fun postboksadresse(postboksadresse: Postboksadresse?) = apply { this.postboksadresse = postboksadresse }
	fun vegadresse(vegadresse: Vegadresse?) = apply { this.vegadresse = vegadresse }
	fun utenlandskAdresse(utenlandskAdresse: UtenlandskAdresse?) = apply { this.utenlandskAdresse = utenlandskAdresse }

	fun build() = Kontaktadresse(
		gyldigFraOgMed = gyldigFraOgMed,
		gyldigTilOgMed = gyldigTilOgMed,
		type = type,
		coAdressenavn = coAdressenavn,
		postboksadresse = postboksadresse,
		vegadresse = vegadresse,
		utenlandskAdresse = utenlandskAdresse,
		metadata = metadata
	)
}
