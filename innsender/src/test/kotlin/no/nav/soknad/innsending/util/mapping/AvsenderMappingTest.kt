package no.nav.soknad.innsending.util.mapping

import no.nav.soknad.innsending.model.AvsenderDto
import no.nav.soknad.innsending.util.mapping.avsender.toArkiveringAvsenderDto
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AvsenderMappingTest {

	@Test
	fun `skal oversette avsender med ORGNR til arkiveringsmodellen`() {
		val avsender = AvsenderDto(
			id = "123456789",
			idType = AvsenderDto.IdType.ORGNR,
			navn = "Testbedrift AS",
		)

		val translated = avsender.toArkiveringAvsenderDto()

		assertEquals(avsender.id, translated.id)
		assertEquals("ORGNR", translated.idType?.name)
		assertEquals(avsender.navn, translated.navn)
	}

	@Test
	fun `skal bruke FNR som fallback nar avsender idType mangler`() {
		val avsender = AvsenderDto(id = "123456789", navn = "Test Avsender")

		val translated = avsender.toArkiveringAvsenderDto()

		assertEquals("FNR", translated.idType?.name)
	}

	@Test
	fun `skal ikke sette fallback idType nar avsender id mangler`() {
		val avsender = AvsenderDto(navn = "Test Avsender")

		val translated = avsender.toArkiveringAvsenderDto()

		assertEquals(null, translated.idType)
	}
}
