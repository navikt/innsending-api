package no.nav.soknad.innsending.consumerapis.arena

import no.nav.soknad.innsending.model.*
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
@Profile("local | docker")
class ArenaConsumerTest : ArenaConsumerInterface {
	override suspend fun getMaalgrupper(): List<Maalgruppe> {
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

	override suspend fun getAktiviteter(): List<Aktivitet> {
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
			saksinformasjon = Saksinformasjon(
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
								betalingsplanId = "14514540",
								beloep = 315,
								utgiftsperiode = Periode(
									fom = LocalDate.now().minusMonths(2),
									tom = LocalDate.now().plusMonths(2)
								),
								journalpostId = "480716180"
							),
							Betalingsplan(
								betalingsplanId = "14514541",
								beloep = 315,
								utgiftsperiode = Periode(
									fom = LocalDate.now().minusMonths(2),
									tom = LocalDate.now().plusMonths(2)
								),
								journalpostId = "480716180"
							)
						)
					)
				)
			)
		)
		return listOf(aktivitet)
	}
}
