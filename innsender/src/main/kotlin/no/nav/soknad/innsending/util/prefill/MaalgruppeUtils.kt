package no.nav.soknad.innsending.util.prefill

import no.nav.soknad.innsending.model.Aktivitet
import no.nav.soknad.innsending.model.Maalgruppe
import no.nav.soknad.innsending.model.MaalgruppeType
import org.slf4j.LoggerFactory
import java.time.LocalDate

data class MaalgruppePriority(
	val maalgruppetype: MaalgruppeType,
	val maalgruppenavn: String,
	val priority: Int,
)

object MaalgruppeUtils {
	private val logger = LoggerFactory.getLogger(javaClass)

	private val maalgruppePriorities = listOf(
		MaalgruppePriority(
			maalgruppetype = MaalgruppeType.NEDSARBEVN,
			priority = 1,
			maalgruppenavn = "Person med nedsatt arbeidsevne pga. sykdom"
		),
		MaalgruppePriority(
			maalgruppetype = MaalgruppeType.ENSFORUTD,
			priority = 2,
			maalgruppenavn = "Enslig forsørger under utdanning"
		),
		MaalgruppePriority(
			maalgruppetype = MaalgruppeType.ENSFORARBS,
			priority = 3,
			maalgruppenavn = "Enslig forsørger som søker arbeid"
		),
		MaalgruppePriority(
			maalgruppetype = MaalgruppeType.TIDLFAMPL,
			priority = 4,
			maalgruppenavn = "Tidligere familiepleier under utdanning"
		),
		MaalgruppePriority(
			maalgruppetype = MaalgruppeType.GJENEKUTD,
			priority = 5,
			maalgruppenavn = "Gjenlevende ektefelle under utdanning"
		),
		MaalgruppePriority(
			maalgruppetype = MaalgruppeType.GJENEKARBS,
			priority = 6,
			maalgruppenavn = "Gjenlevende ektefelle som søker arbeid"
		),
		MaalgruppePriority(
			maalgruppetype = MaalgruppeType.MOTTILTPEN,
			priority = 7,
			maalgruppenavn = "Mottaker av tiltakspenger"
		),
		MaalgruppePriority(
			maalgruppetype = MaalgruppeType.MOTDAGPEN,
			priority = 8,
			maalgruppenavn = "Mottaker av dagpenger"
		),
		MaalgruppePriority(
			maalgruppetype = MaalgruppeType.ARBSOKERE,
			priority = 9,
			maalgruppenavn = "Arbeidssøker"
		),
		MaalgruppePriority(
			maalgruppetype = MaalgruppeType.ANNET,
			priority = 10,
			maalgruppenavn = "Annet"
		)
	)

	fun getPrioritzedMaalgruppe(maalgrupper: List<Maalgruppe>): Maalgruppe? {
		if (maalgrupper.isEmpty()) return null
		val prioritizedMaalgruppe = maalgruppePriorities
			.filter { maalgruppePriority ->
				maalgrupper.any { it.maalgruppetype == maalgruppePriority.maalgruppetype }
			}
			.minByOrNull { it.priority }

		return prioritizedMaalgruppe?.let { priority ->
			maalgrupper.find { it.maalgruppetype == priority.maalgruppetype }
		}

	}

	// Find målgruppe with a periode that overlaps with the periode for aktivitet
	fun getPrioritzedMaalgruppeFromAktivitet(maalgrupper: List<Maalgruppe>, aktivitet: Aktivitet): Maalgruppe? {
		if (maalgrupper.isEmpty()) return null

		val overlappingMaalgrupper = maalgrupper.filter { maalgruppe -> isOverlapping(maalgruppe, aktivitet) }
		if (overlappingMaalgrupper.isNotEmpty()) {
			logger.info("Fant overlappende målgruppe(r) for aktivitet $overlappingMaalgrupper")
			return getPrioritzedMaalgruppe(overlappingMaalgrupper)
		}

		return null
	}

	fun isOverlapping(maalgruppe: Maalgruppe, aktivitet: Aktivitet): Boolean {
		val aktivitetStart = aktivitet.periode.fom
		val aktivitetEnd = aktivitet.periode.tom ?: LocalDate.now()

		val maalgruppeStart = maalgruppe.gyldighetsperiode.fom
		val maalgruppeEnd = maalgruppe.gyldighetsperiode.tom ?: LocalDate.now()

		return isDateBetween(date = aktivitetStart, start = maalgruppeStart, end = maalgruppeEnd) ||
			isDateBetween(date = aktivitetEnd, start = maalgruppeStart, end = maalgruppeEnd) ||
			isDateBetween(date = maalgruppeStart, start = aktivitetStart, end = aktivitetEnd) ||
			isDateBetween(date = maalgruppeEnd, start = aktivitetStart, end = aktivitetEnd)
	}

	private fun isDateBetween(date: LocalDate, start: LocalDate, end: LocalDate): Boolean {
		return date in start..end
	}
}



