package no.nav.soknad.innsending.consumerapis.arena

import no.nav.soknad.innsending.model.*
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
@Profile("local | docker")
class ArenaConsumerTest : ArenaConsumerInterface {
	override fun getMaalgrupper(): List<Maalgruppe> {
		val maalgruppe =
			Maalgruppe(
				gyldighetsperiode = Periode(
					fom = LocalDate.now().minusMonths(2),
					tom = LocalDate.now().plusMonths(2)
				),
				maalgruppetype = MaalgruppeType.ARBSOKERE,
				maalgruppenavn = "Arbeidssøker",
			)
		return listOf(maalgruppe)
	}

	fun createSaksinformasjon(): Saksinformasjon {
		return Saksinformasjon(
			saksnummerArena = "12837895",
			sakstype = "TSR",
			vedtaksinformasjon = listOf(
				Vedtaksinformasjon(
					vedtakId = "34359921",
					dagsats = 63,
					periode = Periode(
						fom = LocalDate.now().minusMonths(2),
						tom = LocalDate.now().plusMonths(2)
					),
					trengerParkering = false,
					betalingsplan = listOf(
						Betalingsplan(
							betalingsplanId = "14514541",
							beloep = 315,
							utgiftsperiode = Periode(
								fom = LocalDate.now().minusMonths(2).plusWeeks(1),
								tom = LocalDate.now().minusMonths(2).plusWeeks(2).minusDays(1)
							),
						),
						Betalingsplan(
							betalingsplanId = "14514542",
							beloep = 315,
							utgiftsperiode = Periode(
								fom = LocalDate.now().minusMonths(2).plusWeeks(2),
								tom = LocalDate.now().minusMonths(2).plusWeeks(3).minusDays(1)
							),
						),

						// Already paid
						Betalingsplan(
							betalingsplanId = "14514540",
							beloep = 315,
							utgiftsperiode = Periode(
								fom = LocalDate.now().minusMonths(2),
								tom = LocalDate.now().minusMonths(2).plusWeeks(1).minusDays(1)
							),
							journalpostId = "480716180"
						)
					)
				)
			)
		)
	}

	override fun getAktiviteter(type: AktivitetType): List<Aktivitet> {
		val aktivitet = Aktivitet(
			aktivitetId = "130892484",
			aktivitetstype = "ARBTREN",
			aktivitetsnavn = "Arbeidstrening",
			periode = Periode(
				fom = LocalDate.now().minusMonths(2),
				tom = LocalDate.now().plusMonths(2)
			),
			antallDagerPerUke = 5,
			prosentAktivitetsdeltakelse = 100,
			aktivitetsstatus = "FULLF",
			aktivitetsstatusnavn = "Fullført",
			erStoenadsberettigetAktivitet = true,
			erUtdanningsaktivitet = false,
			arrangoer = "MOELV BIL & CARAVAN AS",
			saksinformasjon = if (type == AktivitetType.dagligreise) createSaksinformasjon() else null
		)
		return listOf(aktivitet)
	}
}
