package no.nav.soknad.innsending.service.unittest

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import no.nav.soknad.innsending.consumerapis.skjema.HentSkjemaDataConsumer
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.service.KodeverkService
import no.nav.soknad.innsending.service.SkjemaService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ExtendWith(MockKExtension::class)
class SkjemaServiceUnitTest {

	@MockK
	lateinit var hentSkjemaDataConsumer: HentSkjemaDataConsumer

	@MockK
	lateinit var kodeverkService: KodeverkService

	@InjectMockKs
	lateinit var skjemaService: SkjemaService

	@Test
	fun `uses Kodeverk when attachment code is missing from schema data`() {
		every {
			hentSkjemaDataConsumer.hentSkjemaEllerVedlegg("DD", "nb")
		} throws BackendErrorException("Schema not found")
		every { kodeverkService.getAttachmentTitle("DD", "nb") } returns "Documentation"

		val result = skjemaService.hentSkjema("DD", "nb")

		assertEquals("DD", result.skjemanummer)
		assertEquals("Documentation", result.tittel)
		assertNull(result.url)
		assertNull(result.tema)
	}

	@Test
	fun `rejects identifiers missing from schema data and Kodeverk`() {
		every {
			hentSkjemaDataConsumer.hentSkjemaEllerVedlegg("UNKNOWN", "nb")
		} throws BackendErrorException("Schema not found")
		every { kodeverkService.getAttachmentTitle("UNKNOWN", "nb") } returns null

		assertThrows<ResourceNotFoundException> {
			skjemaService.hentSkjema("UNKNOWN", "nb")
		}
	}
}
