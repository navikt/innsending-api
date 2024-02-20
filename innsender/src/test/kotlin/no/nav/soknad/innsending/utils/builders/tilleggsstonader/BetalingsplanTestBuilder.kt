package no.nav.soknad.innsending.utils.builders.tilleggsstonader

import no.nav.soknad.innsending.model.Betalingsplan
import no.nav.soknad.innsending.model.Periode
import java.time.LocalDate

class BetalingsplanTestBuilder {
	private var betalingsplanId: String = "14514541"
	private var beloep: Long = 315
	private var utgiftsperiode: Periode = Periode(LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1))
	private var journalpostId: String? = "480716180"

	fun betalingsplanId(betalingsplanId: String) = apply { this.betalingsplanId = betalingsplanId }
	fun beloep(beloep: Long) = apply { this.beloep = beloep }
	fun utgiftsperiode(utgiftsperiode: Periode) = apply { this.utgiftsperiode = utgiftsperiode }
	fun journalpostId(journalpostId: String?) = apply { this.journalpostId = journalpostId }

	fun build() =
		Betalingsplan(
			betalingsplanId = betalingsplanId,
			beloep = beloep,
			utgiftsperiode = utgiftsperiode,
			journalpostId = journalpostId
		)
}
