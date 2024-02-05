package no.nav.soknad.innsending.utils.builders.tilleggsstonader

import no.nav.soknad.innsending.model.Betalingsplan
import no.nav.soknad.innsending.model.Periode
import no.nav.soknad.innsending.model.Vedtaksinformasjon
import java.time.LocalDate

class VedtaksinformasjonTestBuilder {
	private var vedtakId: String = "34359921"
	private var dagsats: Long = 63
	private var periode: Periode = Periode(LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1))
	private var trengerParkering: Boolean = false
	private var forventetDagligParkeringsutgift: Long? = 500
	private var betalingsplan: List<Betalingsplan>? = null

	fun vedtakId(vedtakId: String) = apply { this.vedtakId = vedtakId }
	fun dagsats(dagsats: Long) = apply { this.dagsats = dagsats }
	fun periode(periode: Periode) = apply { this.periode = periode }
	fun trengerParkering(trengerParkering: Boolean) = apply { this.trengerParkering = trengerParkering }
	fun forventetDagligParkeringsutgift(forventetDagligParkeringsutgift: Long?) =
		apply { this.forventetDagligParkeringsutgift = forventetDagligParkeringsutgift }

	fun betalingsplan(betalingsplan: List<Betalingsplan>?) = apply { this.betalingsplan = betalingsplan }

	fun build() =
		Vedtaksinformasjon(
			vedtakId = vedtakId,
			dagsats = dagsats,
			periode = periode,
			trengerParkering = trengerParkering,
			forventetDagligParkeringsutgift = forventetDagligParkeringsutgift,
			betalingsplan = betalingsplan
		)
}
