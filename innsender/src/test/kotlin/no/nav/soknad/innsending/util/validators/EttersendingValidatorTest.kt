package no.nav.soknad.innsending.util.validators

import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.kodeverk.KodeverkType
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.utils.builders.ettersending.InnsendtVedleggDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.ettersending.OpprettEttersendingTestBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired

class EttersendingValidatorTest : ApplicationTest() {

	@Autowired
	private lateinit var ettersendingValidator: EttersendingValidator

	@Test
	fun `Should not throw exception if skjemanr is valid`() {
		// Given
		val ettersending = OpprettEttersendingTestBuilder().skjemanr("NAV 02-07.05").build()
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
		val vedlegg = listOf(InnsendtVedleggDtoTestBuilder().vedleggsnr("N6").build())
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

	@Test
	fun `Should continue execution if kodeverk request fails`() {
		// Given
		val ettersending = OpprettEttersendingTestBuilder().skjemanr("invalid").build()
		val kodeverkTypes = listOf(KodeverkType.KODEVERK_NAVSKJEMA)
		WireMock.setScenarioState("kodeverk-navskjema", "failed")

		// When
		assertDoesNotThrow {
			ettersendingValidator.validateEttersending(ettersending, kodeverkTypes)
		}

	}

}