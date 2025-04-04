package no.nav.soknad.innsending.util.prefill

import no.nav.soknad.innsending.model.Maalgruppe
import no.nav.soknad.innsending.model.MaalgruppeType
import no.nav.soknad.innsending.model.Periode
import no.nav.soknad.innsending.utils.builders.tilleggsstonader.AktivitetTestBuilder
import no.nav.soknad.innsending.utils.builders.tilleggsstonader.MaalgruppeTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MaalgruppeUtilsTest {

	val now = LocalDate.now()

	val oneMonthAgo = LocalDate.now().minusMonths(1)
	val twoMonthsAgo = LocalDate.now().minusMonths(2)
	val threeMonthsAgo = LocalDate.now().minusMonths(3)

	val oneMonthFromNow = LocalDate.now().plusMonths(1)
	val twoMonthsFromNow = LocalDate.now().plusMonths(2)
	val threeMonthsFromNow = LocalDate.now().plusMonths(3)

	// ==== getPrioritzedMaalgruppe() tests ====
	@Test
	fun `Should return highest priority maalgruppe when multiple maalgrupper are given`() {
		// Given
		val maalgrupper = listOf(
			MaalgruppeTestBuilder().maalgruppetype(MaalgruppeType.ARBSOKERE).build(), // Priority 9
			MaalgruppeTestBuilder().maalgruppetype(MaalgruppeType.ENSFORUTD).build(), // Priority 2
			MaalgruppeTestBuilder().maalgruppetype(MaalgruppeType.GJENEKARBS).build(), // Priority 6
		)

		// When
		val result = MaalgruppeUtils.getPrioritzedMaalgruppe(maalgrupper)

		// Then
		assertEquals(MaalgruppeType.ENSFORUTD, result?.maalgruppetype)
	}

	@Test
	fun `Should return null when no maalgrupper are given`() {
		// Given
		val maalgrupper = emptyList<Maalgruppe>()

		// When
		val result = MaalgruppeUtils.getPrioritzedMaalgruppe(maalgrupper)

		// Then
		assertEquals(null, result)
	}

	// ==== isOverlapping() tests ====
	@Test
	fun `Should overlap when periodes are equal`() {
		// Given
		val maalgruppe = MaalgruppeTestBuilder().gyldighetsperiode(Periode(oneMonthAgo, oneMonthFromNow)).build()
		val aktivitet = AktivitetTestBuilder().periode(Periode(oneMonthAgo, oneMonthFromNow)).build()

		// When / Then
		assertTrue(MaalgruppeUtils.isOverlapping(maalgruppe, aktivitet))
	}

	@Test
	fun `Should overlap målgruppe periode is inside aktivitet periode`() {
		// Given
		val maalgruppe = MaalgruppeTestBuilder().gyldighetsperiode(Periode(oneMonthAgo, oneMonthFromNow)).build()
		val aktivitet = AktivitetTestBuilder().periode(Periode(twoMonthsAgo, twoMonthsFromNow)).build()

		// When / Then
		assertTrue(MaalgruppeUtils.isOverlapping(maalgruppe, aktivitet))
	}

	@Test
	fun `Should overlap when aktivitet has end-date inside målgruppe periode`() {
		// Given
		val maalgruppe = MaalgruppeTestBuilder().gyldighetsperiode(Periode(oneMonthAgo, oneMonthFromNow)).build()
		val aktivitet = AktivitetTestBuilder().periode(Periode(twoMonthsAgo, now)).build()

		// When / Then
		assertTrue(MaalgruppeUtils.isOverlapping(maalgruppe, aktivitet))
	}

	@Test
	fun `Should overlap when aktivitet has start-date inside målgruppe periode`() {
		// Given
		val maalgruppe = MaalgruppeTestBuilder().gyldighetsperiode(Periode(twoMonthsAgo, oneMonthFromNow)).build()
		val aktivitet = AktivitetTestBuilder().periode(Periode(oneMonthAgo, twoMonthsFromNow)).build()

		// When / Then
		assertTrue(MaalgruppeUtils.isOverlapping(maalgruppe, aktivitet))
	}

	@Test
	fun `Should overlap when målgruppe has start-date inside aktivitet periode`() {
		// Given
		val maalgruppe = MaalgruppeTestBuilder().gyldighetsperiode(Periode(now, twoMonthsFromNow)).build()
		val aktivitet = AktivitetTestBuilder().periode(Periode(twoMonthsAgo, oneMonthFromNow)).build()

		// When / Then
		assertTrue(MaalgruppeUtils.isOverlapping(maalgruppe, aktivitet))
	}

	@Test
	fun `Should overlap when målgruppe has end-date inside aktivitet periode`() {
		// Given
		val maalgruppe = MaalgruppeTestBuilder().gyldighetsperiode(Periode(twoMonthsAgo, now)).build()
		val aktivitet = AktivitetTestBuilder().periode(Periode(oneMonthAgo, twoMonthsFromNow)).build()

		// When / Then
		assertTrue(MaalgruppeUtils.isOverlapping(maalgruppe, aktivitet))
	}

	@Test
	fun `Should not overlap when maalgruppe periode is before aktivitet`() {
		// Given
		val maalgruppe = MaalgruppeTestBuilder().gyldighetsperiode(Periode(threeMonthsAgo, twoMonthsAgo)).build()
		val aktivitet = AktivitetTestBuilder().periode(Periode(oneMonthAgo, oneMonthFromNow)).build()

		// When / Then
		assertFalse(MaalgruppeUtils.isOverlapping(maalgruppe, aktivitet))
	}

	@Test
	fun `Should not overlap when maalgruppe periode is after aktivitet`() {
		// Given
		val maalgruppe = MaalgruppeTestBuilder().gyldighetsperiode(Periode(twoMonthsFromNow, threeMonthsFromNow)).build()
		val aktivitet = AktivitetTestBuilder().periode(Periode(oneMonthAgo, oneMonthFromNow)).build()

		// When / Then
		assertFalse(MaalgruppeUtils.isOverlapping(maalgruppe, aktivitet))
	}

	// ==== getPrioritzedMaalgruppeFromAktivitet() tests ====

	@Test
	fun `Should return null when maalgrupper is empty`() {
		// Given
		val maalgrupper = emptyList<Maalgruppe>()
		val aktivitet = AktivitetTestBuilder().build()

		// When / Then
		assertNull(MaalgruppeUtils.getPrioritzedMaalgruppeFromAktivitet(maalgrupper, aktivitet))
	}

	@Test
	fun `Should return prioritized maalgruppe when all maalgrupper are overlapping`() {
		// Given
		val maalgrupper = listOf(
			MaalgruppeTestBuilder()
				.maalgruppetype(MaalgruppeType.ENSFORUTD) // Priority 2, overlapping
				.gyldighetsperiode(Periode(oneMonthAgo, oneMonthFromNow)).build(),
			MaalgruppeTestBuilder()
				.maalgruppetype(MaalgruppeType.NEDSARBEVN) // Priority 1, overlapping
				.gyldighetsperiode(Periode(oneMonthAgo, oneMonthFromNow)).build(),
			MaalgruppeTestBuilder()
				.maalgruppetype(MaalgruppeType.ARBSOKERE) // Priority 9, overlapping
				.gyldighetsperiode(Periode(oneMonthAgo, oneMonthFromNow)).build()
		)

		// When
		val aktivitet = AktivitetTestBuilder().periode(Periode(oneMonthAgo, oneMonthFromNow)).build()

		// Then
		assertEquals(
			MaalgruppeType.NEDSARBEVN,
			MaalgruppeUtils.getPrioritzedMaalgruppeFromAktivitet(maalgrupper, aktivitet)?.maalgruppetype
		)
	}

	@Test
	fun `Should return the less prioritzed målgruppe when it is overlapping (and the more prioritized målgruppe is not overlapping)`() {
		// Given
		val maalgrupper = listOf(
			MaalgruppeTestBuilder()
				.maalgruppetype(MaalgruppeType.NEDSARBEVN) // Priority 1, not overlapping
				.gyldighetsperiode(Periode(threeMonthsAgo, twoMonthsAgo)).build(),
			MaalgruppeTestBuilder()
				.maalgruppetype(MaalgruppeType.ANNET) // Priority 10, overlapping
				.gyldighetsperiode(Periode(now, oneMonthFromNow)).build()
		)

		// When
		val aktivitet = AktivitetTestBuilder().periode(Periode(oneMonthAgo, oneMonthFromNow)).build()

		// Then
		assertEquals(
			MaalgruppeType.ANNET,
			MaalgruppeUtils.getPrioritzedMaalgruppeFromAktivitet(maalgrupper, aktivitet)?.maalgruppetype
		)
	}

	@Test
	fun `Should return null when no overlapping maalgrupper are present`() {
		// Given
		val maalgrupper = listOf(
			MaalgruppeTestBuilder().maalgruppetype(MaalgruppeType.GJENEKUTD) // Priority 5, not overlapping
				.gyldighetsperiode(Periode(threeMonthsAgo, twoMonthsAgo)).build(),
			MaalgruppeTestBuilder().maalgruppetype(MaalgruppeType.TIDLFAMPL) // Priority 4, not overlapping
				.gyldighetsperiode(Periode(threeMonthsAgo, twoMonthsAgo)).build(),
		)

		// When
		val aktivitet = AktivitetTestBuilder().periode(Periode(oneMonthAgo, oneMonthFromNow)).build()

		// Then
		assertNull(MaalgruppeUtils.getPrioritzedMaalgruppeFromAktivitet(maalgrupper, aktivitet))
	}


}
