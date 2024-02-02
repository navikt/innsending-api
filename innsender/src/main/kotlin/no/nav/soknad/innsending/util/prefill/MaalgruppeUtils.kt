package no.nav.soknad.innsending.util.prefill

import no.nav.soknad.innsending.model.Maalgruppe

data class MaalgruppePriority(
	val maalgruppetype: String,
	val maalgruppenavn: String,
	val priority: Int,
)

object MaalgruppeUtils {
	private val maalgruppePriorities = listOf(
		MaalgruppePriority(
			maalgruppetype = "NEDSARBEVN",
			priority = 1,
			maalgruppenavn = "Person med nedsatt arbeidsevne pga. sykdom"
		),
		MaalgruppePriority(maalgruppetype = "ENSFORUTD", priority = 2, maalgruppenavn = "Enslig forsørger under utdanning"),
		MaalgruppePriority(
			maalgruppetype = "ENSFORARBS",
			priority = 3,
			maalgruppenavn = "Enslig forsørger som søker arbeid"
		),
		MaalgruppePriority(
			maalgruppetype = "TIDLFAMPL",
			priority = 4,
			maalgruppenavn = "Tidligere familiepleier under utdanning"
		),
		MaalgruppePriority(
			maalgruppetype = "GJENEKUTD",
			priority = 5,
			maalgruppenavn = "Gjenlevende ektefelle under utdanning"
		),
		MaalgruppePriority(
			maalgruppetype = "GJENEKARBS",
			priority = 6,
			maalgruppenavn = "Gjenlevende ektefelle som søker arbeid"
		),
		MaalgruppePriority(
			maalgruppetype = "MOTTILTPEN",
			priority = 7,
			maalgruppenavn = "Mottaker av tiltakspenger"
		),
		MaalgruppePriority(
			maalgruppetype = "MOTDAGPEN",
			priority = 8,
			maalgruppenavn = "Mottaker av dagpenger"
		),
		MaalgruppePriority(
			maalgruppetype = "ARBSOKERE",
			priority = 9,
			maalgruppenavn = "Arbeidssøker"
		),
		MaalgruppePriority(
			maalgruppetype = "ANNET",
			priority = 10,
			maalgruppenavn = "Annet"
		)
	)

	fun getPrioritzedMaalgruppe(maalgrupper: List<Maalgruppe>): MaalgruppePriority? {
		if (maalgrupper.isEmpty()) return null
		return maalgruppePriorities
			.filter { maalgrupper.any { maalgruppe -> maalgruppe.maalgruppetype?.value == it.maalgruppetype } }
			.minBy { it.priority }
	}
}



