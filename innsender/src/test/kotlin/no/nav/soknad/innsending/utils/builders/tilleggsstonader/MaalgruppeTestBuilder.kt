package no.nav.soknad.innsending.utils.builders.tilleggsstonader

import no.nav.soknad.innsending.model.Maalgruppe
import no.nav.soknad.innsending.model.MaalgruppeType
import no.nav.soknad.innsending.model.Periode
import java.time.LocalDate

class MaalgruppeTestBuilder(
	var gyldighetsperiode: Periode = Periode(LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1)),
	var maalgruppetype: MaalgruppeType = MaalgruppeType.ARBSOKERE,
	var maalgruppenavn: String = "Arbeidssøker"
) {
	fun gyldighetsperiode(gyldighetsperiode: Periode) = apply { this.gyldighetsperiode = gyldighetsperiode }
	fun maalgruppetype(maalgruppetype: MaalgruppeType) = apply { this.maalgruppetype = maalgruppetype }
	fun maalgruppenavn(maalgruppenavn: String) = apply { this.maalgruppenavn = maalgruppenavn }

	fun build() = Maalgruppe(
		gyldighetsperiode = gyldighetsperiode,
		maalgruppetype = maalgruppetype,
		maalgruppenavn = maalgruppenavn
	)
}

