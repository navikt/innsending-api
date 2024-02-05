package no.nav.soknad.innsending.util.prefill

import no.nav.soknad.innsending.model.Maalgruppe
import no.nav.soknad.innsending.model.MaalgruppeType
import no.nav.soknad.innsending.utils.builders.tilleggsstonader.MaalgruppeTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MaalgruppeUtilsTest {

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
		assertEquals(MaalgruppeType.ENSFORUTD, result)
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

}
