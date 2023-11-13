package no.nav.soknad.innsending.util.mapping

import no.nav.soknad.innsending.util.Constants.DEFAULT_LEVETID_OPPRETTET_SOKNAD
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SoknadTest {
	@Test
	fun `Should map to SkjemaDto correctly`() {
		// Given
		val dokumentSoknadDto = DokumentSoknadDtoTestBuilder().build()

		// When
		val result = mapTilSkjemaDto(dokumentSoknadDto)

		// Then
		assertEquals(dokumentSoknadDto.innsendingsId, result.innsendingsId)
		assertEquals(
			dokumentSoknadDto.opprettetDato.plusDays(DEFAULT_LEVETID_OPPRETTET_SOKNAD).toLocalDate().toString(),
			result.skalSlettesDato?.toString(),
		)
	}
}
