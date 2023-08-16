package no.nav.soknad.innsending.utils.validators

import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.util.validators.validerSoknadVedOppdatering
import no.nav.soknad.innsending.util.validators.validerVedleggsListeVedOppdatering
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.VedleggDtoTestBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class SoknadValidatorTest {

	private fun lagEksisterendeSoknad(formioId: String? = null): DokumentSoknadDto {
		val vedleggDto = VedleggDtoTestBuilder(formioId = formioId).build()
		return DokumentSoknadDtoTestBuilder(vedleggsListe = listOf(vedleggDto)).build()
	}

	@Test
	fun `Skal kaste exception når skjemanr er ulike`() {
		// Gitt
		val soknad = DokumentSoknadDtoTestBuilder(skjemanr = "ulikt skjemanr").build()
		val eksisterendeSoknad = lagEksisterendeSoknad()

		// Så
		val exception = assertThrows<IllegalActionException> {
			soknad.validerSoknadVedOppdatering(eksisterendeSoknad)
		}

		assertEquals("Felter er ikke like for DokumentSoknadDto: skjemanr", exception.message)

	}

	@Test
	fun `Skal ikke kaste exception når skjemanr og brukerid er like`() {
		// Gitt
		val eksisterendeSoknad = lagEksisterendeSoknad()
		val soknad = DokumentSoknadDtoTestBuilder(
			brukerId = eksisterendeSoknad.brukerId,
			skjemanr = eksisterendeSoknad.skjemanr
		).build()

		// Så
		assertDoesNotThrow {
			soknad.validerVedleggsListeVedOppdatering(eksisterendeSoknad)
		}
	}

	@Test
	fun `Skal kaste exception når vedlegg er ulike`() {
		// Gitt
		val formioId = "formioId"
		val vedleggDto = VedleggDtoTestBuilder(formioId = formioId, vedleggsnr = "annet").build()
		val soknad = DokumentSoknadDtoTestBuilder(vedleggsListe = listOf(vedleggDto)).build()

		val eksisterendeSoknad = lagEksisterendeSoknad(formioId)

		// Så
		val exception = assertThrows<IllegalActionException> {
			soknad.validerVedleggsListeVedOppdatering(eksisterendeSoknad)
		}
		assertEquals("Felter er ikke like for VedleggDto: vedleggsnr", exception.message)

	}

	@Test
	fun `Skal ikke kaste exception når vedlegg er like`() {
		// Gitt
		val vedleggDto = VedleggDtoTestBuilder().build()
		val soknad = DokumentSoknadDtoTestBuilder(vedleggsListe = listOf(vedleggDto)).build()

		val eksisterendeSoknad = lagEksisterendeSoknad()

		// Så
		assertDoesNotThrow {
			soknad.validerVedleggsListeVedOppdatering(eksisterendeSoknad)
		}
	}

}
