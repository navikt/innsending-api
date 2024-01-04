package no.nav.soknad.innsending.service

import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.kodeverk.KodeverkType
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.utils.builders.ettersending.InnsendtVedleggDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.ettersending.OpprettEttersendingTestBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

class KodeverkServiceTest : ApplicationTest() {

	@Autowired
	private lateinit var kodeverkService: KodeverkService

	val validSkjemanr = "NAV 02-07.05"
	val validTema = "AAP"
	val validVedleggsnr = "N6"

	@Test
	fun `Should not throw exception if skjemanr is valid`() {
		// Given
		val ettersending = OpprettEttersendingTestBuilder().skjemanr(validSkjemanr).build()
		val kodeverkTypes = listOf(KodeverkType.KODEVERK_NAVSKJEMA)

		// When / Then
		assertDoesNotThrow {
			kodeverkService.validateEttersending(ettersending, kodeverkTypes)
		}
	}

	@Test
	fun `Should throw exception if skjemanr is invalid`() {
		// Given
		val ettersending = OpprettEttersendingTestBuilder().skjemanr("invalid").build()
		val kodeverkTypes = listOf(KodeverkType.KODEVERK_NAVSKJEMA)

		// When / Then
		assertThrows<IllegalActionException> {
			kodeverkService.validateEttersending(ettersending, kodeverkTypes)
		}
	}

	@Test
	fun `Should not throw exception if tema is valid`() {
		// Given
		val ettersending = OpprettEttersendingTestBuilder().tema(validTema).build()
		val kodeverkTypes = listOf(KodeverkType.KODEVERK_TEMA)

		// When / Then
		assertDoesNotThrow {
			kodeverkService.validateEttersending(ettersending, kodeverkTypes)
		}
	}

	@Test
	fun `Should throw exception if tema is invalid`() {
		// Given
		val ettersending = OpprettEttersendingTestBuilder().tema("invalidTema").build()
		val kodeverkTypes = listOf(KodeverkType.KODEVERK_TEMA)

		// When / Then
		assertThrows<IllegalActionException> {
			kodeverkService.validateEttersending(ettersending, kodeverkTypes)
		}
	}

	@Test
	fun `Should not throw exception if vedleggsnr is valid`() {
		// Given
		val vedlegg = listOf(InnsendtVedleggDtoTestBuilder().vedleggsnr(validVedleggsnr).build())
		val ettersending = OpprettEttersendingTestBuilder().vedleggsListe(vedlegg).build()
		val kodeverkTypes = listOf(KodeverkType.KODEVERK_VEDLEGGSKODER)

		// When / Then
		assertDoesNotThrow {
			kodeverkService.validateEttersending(ettersending, kodeverkTypes)
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
			kodeverkService.validateEttersending(ettersending, kodeverkTypes)
		}
	}

	@Test
	fun `Should throw exception if kodeverk request fails and cache is empty`() {
		// Given
		val ettersending = OpprettEttersendingTestBuilder().skjemanr("invalid").build()
		val kodeverkTypes = listOf(KodeverkType.KODEVERK_NAVSKJEMA)
		WireMock.setScenarioState("kodeverk-navskjema", "failed")

		// When / Then
		assertThrows<BackendErrorException> {
			kodeverkService.cache.invalidateAll()
			kodeverkService.validateEttersending(ettersending, kodeverkTypes)
		}
	}

	@Test
	fun `Should use old cache if kodeverk request fails`() {
		// Given
		val ettersending = OpprettEttersendingTestBuilder().skjemanr(validSkjemanr).build()
		val kodeverkTypes = listOf(KodeverkType.KODEVERK_NAVSKJEMA)

		// When / Then

		assertDoesNotThrow {
			kodeverkService.validateEttersending(ettersending, kodeverkTypes)
		}

		// Request fails for refreshing
		WireMock.setScenarioState("kodeverk-navskjema", "failed")
		kodeverkService.cache.refresh(KodeverkType.KODEVERK_NAVSKJEMA.value)

		assertDoesNotThrow {
			kodeverkService.validateEttersending(ettersending, kodeverkTypes)
		}

	}

	@Test
	fun `Should add tittel to ettersending from kodeverk if not specified (norwegian)`() {
		// Given
		val ettersending = OpprettEttersendingTestBuilder().tittel(null).skjemanr("NAV 02-07.05").sprak("nb_NO").build()

		// When
		val enrichedEttersending = kodeverkService.enrichEttersendingWithKodeverkInfo(ettersending)

		// Then
		assertEquals("Søknad om å bli medlem i folketrygden under opphold i Norge", enrichedEttersending.tittel)
	}

	@Test
	fun `Should add tittel to ettersending from kodeverk if not specified (english)`() {
		// Given
		val ettersending = OpprettEttersendingTestBuilder().tittel(null).skjemanr("NAV 02-07.05").sprak("en_GB").build()

		// When
		val enrichedEttersending = kodeverkService.enrichEttersendingWithKodeverkInfo(ettersending)

		// Then
		assertEquals("Application for insurance during stay in Norway", enrichedEttersending.tittel)
	}

	@Test
	fun `Should add tittel to vedlegg from kodeverk if not specified (norwegian)`() {
		// Given
		val ettersending = OpprettEttersendingTestBuilder()
			.skjemanr("NAV 02-07.05")
			.vedleggsListe(listOf(InnsendtVedleggDtoTestBuilder().tittel(null).vedleggsnr("N6").build()))
			.sprak("nb_NO")
			.build()

		// When
		val enrichedEttersending = kodeverkService.enrichEttersendingWithKodeverkInfo(ettersending)

		// Then
		assertEquals("Annet", enrichedEttersending.vedleggsListe?.get(0)?.tittel)
	}


}
