package no.nav.soknad.innsending.service.unittest

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.service.KodeverkService
import no.nav.soknad.innsending.service.SkjemaService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class SkjemaServiceUnitTest {

	@MockK
	lateinit var kodeverkService: KodeverkService

	@InjectMockKs
	lateinit var skjemaService: SkjemaService

	@Test
	fun `uses Kodeverk title for attachment`() {
		every { kodeverkService.getAttachmentTitle("N6", "nb") } returns "Annet"

		val result = skjemaService.hentVedlegg("N6", "nb")

		assertEquals("N6", result.skjemanummer)
		assertEquals("Annet", result.tittel)
	}

	@Test
	fun `uses Kodeverk for attachment absent from old schema data`() {
		every { kodeverkService.getAttachmentTitle("DD", "nb") } returns "Documentation"

		val result = skjemaService.hentVedlegg("DD", "nb")

		assertEquals("DD", result.skjemanummer)
		assertEquals("Documentation", result.tittel)
	}

	@Test
	fun `uses Kodeverk title for form`() {
		every { kodeverkService.getFormTitle("NAV 55-00.60", "en") } returns "Child support agreement"

		val result = skjemaService.hentSkjema("NAV 55-00.60", "en")

		assertEquals("NAV 55-00.60", result.skjemanummer)
		assertEquals("Child support agreement", result.tittel)
	}

	@Test
	fun `rejects attachment missing from Kodeverk`() {
		every { kodeverkService.getAttachmentTitle("UNKNOWN", "nb") } returns null

		assertThrows<ResourceNotFoundException> {
			skjemaService.hentVedlegg("UNKNOWN", "nb")
		}
	}
}
