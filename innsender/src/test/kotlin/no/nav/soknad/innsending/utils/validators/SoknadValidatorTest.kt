package no.nav.soknad.innsending.utils.validators

import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.util.validators.validerSoknadVedOppdatering
import no.nav.soknad.innsending.util.validators.validerVedleggsListeVedOppdatering
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.OffsetDateTime
import kotlin.test.assertEquals

class SoknadValidatorTest {

	// Søknad
	private val brukerId = "123"
	private val skjemanr = "NAV 123"
	private val tittel = "Tittel"
	private val tema = "Tema"
	private val spraak = "nb_NO"
	private val status = SoknadsStatusDto.opprettet
	private val opprettetDato = OffsetDateTime.now()
	private val vedleggsListe = emptyList<VedleggDto>()

	// Vedlegg
	private val tittelVedlegg = "Tittel"
	private val labelVedlegg = "Label"
	private val erHoveddokument = true
	private val erVariant = false
	private val erPdfa = false
	private val erPakrevd = false
	private val opprettetdato = OffsetDateTime.now()
	private val opplastingsStatus = OpplastingsStatusDto.ikkeValgt

	private fun lagEksisterendeSoknad(): DokumentSoknadDto {
		val vedleggDto = VedleggDto(
			tittel = tittelVedlegg,
			label = labelVedlegg,
			erHoveddokument = erHoveddokument,
			erVariant = erVariant,
			erPdfa = erPdfa,
			erPakrevd = erPakrevd,
			opplastingsStatus = opplastingsStatus,
			opprettetdato = opprettetdato,
		)

		return DokumentSoknadDto(
			brukerId = brukerId,
			skjemanr = skjemanr,
			tittel = tittel,
			tema = tema,
			spraak = spraak,
			status = status,
			opprettetDato = opprettetDato,
			vedleggsListe = listOf(vedleggDto)
		)
	}

	@Test
	fun `Skal kaste exception når skjemanr er ulike`() {

		// Gitt
		val soknad = DokumentSoknadDto(
			brukerId = brukerId,
			skjemanr = "Ulikt skjemanr",
			tittel = tittel,
			tema = tema,
			spraak = spraak,
			status = status,
			opprettetDato = opprettetDato,
			vedleggsListe = vedleggsListe
		)

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
		val soknad = DokumentSoknadDto(
			brukerId = brukerId,
			skjemanr = skjemanr,
			tittel = tittel,
			tema = tema,
			spraak = spraak,
			status = status,
			opprettetDato = opprettetDato,
			vedleggsListe = vedleggsListe
		)

		val eksisterendeSoknad = lagEksisterendeSoknad()

		// Så
		assertDoesNotThrow {
			soknad.validerVedleggsListeVedOppdatering(eksisterendeSoknad)
		}
	}

	@Test
	fun `Skal kaste exception når vedlegg er ulike`() {
		// Gitt
		val vedleggDto = VedleggDto(
			tittel = tittel,
			label = labelVedlegg,
			erHoveddokument = false,
			erVariant = erVariant,
			erPdfa = erPdfa,
			erPakrevd = erPakrevd,
			opplastingsStatus = opplastingsStatus,
			opprettetdato = opprettetdato,
		)
		val soknad = DokumentSoknadDto(
			brukerId = brukerId,
			skjemanr = skjemanr,
			tittel = tittel,
			tema = tema,
			spraak = spraak,
			status = status,
			opprettetDato = opprettetDato,
			vedleggsListe = listOf(vedleggDto)
		)
		val eksisterendeSoknad = lagEksisterendeSoknad()

		// Så
		val exception = assertThrows<IllegalActionException> {
			soknad.validerVedleggsListeVedOppdatering(eksisterendeSoknad)
		}
		assertEquals("Felter er ikke like for VedleggDto: erHoveddokument", exception.message)

	}

	@Test
	fun `Skal ikke kaste exception når vedlegg er like`() {
		// Gitt
		val vedleggDto = VedleggDto(
			tittel = tittel,
			label = labelVedlegg,
			erHoveddokument = erHoveddokument,
			erVariant = erVariant,
			erPdfa = erPdfa,
			erPakrevd = erPakrevd,
			opplastingsStatus = opplastingsStatus,
			opprettetdato = opprettetdato,
		)
		val soknad = DokumentSoknadDto(
			brukerId = brukerId,
			skjemanr = skjemanr,
			tittel = tittel,
			tema = tema,
			spraak = spraak,
			status = status,
			opprettetDato = opprettetDato,
			vedleggsListe = listOf(vedleggDto)
		)
		val eksisterendeSoknad = lagEksisterendeSoknad()

		// Så
		assertDoesNotThrow {
			soknad.validerVedleggsListeVedOppdatering(eksisterendeSoknad)
		}
	}

}
