package no.nav.soknad.innsending.util.mapping

import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SoknadTest {
	@Test
	fun `Should map to SkjemaDto correctly`() {
		// Given
		val mellomlagringDager = 10
		val dokumentSoknadDto =
			DokumentSoknadDtoTestBuilder(
				skalslettesdato = null,
				mellomlagringDager = mellomlagringDager
			).build()

		// When
		val result = mapTilSkjemaDto(dokumentSoknadDto)

		// Then
		assertEquals(dokumentSoknadDto.innsendingsId, result.innsendingsId)
		assertEquals(
			dokumentSoknadDto.opprettetDato.plusDays(mellomlagringDager.toLong()).toInstant(),
			result.skalSlettesDato?.toInstant(),
		)
	}
}
