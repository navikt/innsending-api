package no.nav.soknad.innsending.util.validators

import io.mockk.every
import io.mockk.mockkObject
import no.nav.soknad.innsending.consumerapis.kodeverk.KodeverkType
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.kodeverk.api.KodeverkApi
import no.nav.soknad.innsending.util.MDCUtil
import no.nav.soknad.innsending.utils.builders.ettersending.InnsendtVedleggDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.ettersending.OpprettEttersendingTestBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class EttersendingValidatorTest {

	private lateinit var kodeverkApi: KodeverkApi

	private lateinit var ettersendingValidator: EttersendingValidator

	@BeforeEach
	fun setUp() {
		kodeverkApi = KodeverkApi()
		ettersendingValidator = EttersendingValidator()
		mockkObject(MDCUtil)
		every { MDCUtil.callIdOrNew() } returns "mockedCallId"
	}

	@Test
	fun `Should not throw exception if skjemanr is valid`() {
		// Given
		val ettersending = OpprettEttersendingTestBuilder().skjemanr("NAV 00-00.76").build()
		val kodeverkTypes = listOf(KodeverkType.KODEVERK_NAVSKJEMA)

		// When / Then
		assertDoesNotThrow {
			ettersendingValidator.validateEttersending(ettersending, kodeverkTypes)
		}
	}

	@Test
	fun `Should throw exception if skjemanr is invalid`() {
		// Given
		val ettersending = OpprettEttersendingTestBuilder().skjemanr("invalid").build()
		val kodeverkTypes = listOf(KodeverkType.KODEVERK_NAVSKJEMA)

		// When / Then
		assertThrows<IllegalActionException> {
			ettersendingValidator.validateEttersending(ettersending, kodeverkTypes)
		}
	}

	@Test
	fun `Should not throw exception if tema is valid`() {
		// Given
		val ettersending = OpprettEttersendingTestBuilder().tema("AAP").build()
		val kodeverkTypes = listOf(KodeverkType.KODEVERK_TEMA)

		// When / Then
		assertDoesNotThrow {
			ettersendingValidator.validateEttersending(ettersending, kodeverkTypes)
		}
	}

	@Test
	fun `Should throw exception if tema is invalid`() {
		// Given
		val ettersending = OpprettEttersendingTestBuilder().tema("invalidTema").build()
		val kodeverkTypes = listOf(KodeverkType.KODEVERK_TEMA)

		// When / Then
		assertThrows<IllegalActionException> {
			ettersendingValidator.validateEttersending(ettersending, kodeverkTypes)
		}
	}

	@Test
	fun `Should not throw exception if vedleggsnr is valid`() {
		// Given
		val vedlegg = listOf(InnsendtVedleggDtoTestBuilder().vedleggsnr("AA").build())
		val ettersending = OpprettEttersendingTestBuilder().vedleggsListe(vedlegg).build()
		val kodeverkTypes = listOf(KodeverkType.KODEVERK_VEDLEGGSKODER)

		// When / Then
		assertDoesNotThrow {
			ettersendingValidator.validateEttersending(ettersending, kodeverkTypes)
		}
	}

	@Test
	fun `Should throw exception if vedleggsnr is invalid`() {
		// Given
		val vedlegg = listOf(InnsendtVedleggDtoTestBuilder().vedleggsnr("invalidVedleggsnr").build())
		val ettersending = OpprettEttersendingTestBuilder().vedleggsListe(vedlegg).build()
		val kodeverkTypes = listOf(KodeverkType.KODEVERK_VEDLEGGSKODER)

		// When / Then
		assertThrows<IllegalActionException> {
			ettersendingValidator.validateEttersending(ettersending, kodeverkTypes)
		}
	}
}
