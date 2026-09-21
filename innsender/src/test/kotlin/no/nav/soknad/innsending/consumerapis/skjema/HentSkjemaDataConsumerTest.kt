package no.nav.soknad.innsending.consumerapis.skjema

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.exceptions.BackendErrorException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired

class HentSkjemaDataConsumerTest : ApplicationTest() {

	@Autowired
	lateinit var hentSkjemaDataConsumer: HentSkjemaDataConsumer

	@Test
	fun `Skal hente ut skjema fra endepunkt når cache ikke er oppdatert`() {
		// Når
		val skjema = hentSkjemaDataConsumer.hentSkjemaEllerVedlegg("NAV 14-05.07")

		// Så
		assertEquals("NAV 14-05.07", skjema.skjemanummer)
		assertEquals("FOR", skjema.tema)
	}

	@Test
	fun `Skal hente ut skjema fra cache`() {
		// Gitt
		val cachetSkjema = SkjemaOgVedleggsdata(skjemanummer = "NAV 123", tema = "TEMA")
		hentSkjemaDataConsumer.cache.put("sanityList", listOf(cachetSkjema))

		// Når
		val skjema = hentSkjemaDataConsumer.hentSkjemaEllerVedlegg("NAV 123")

		// Så
		assertEquals("NAV 123", skjema.skjemanummer)
		assertEquals("TEMA", skjema.tema)
	}

	@Test
	fun `Skal kaste exception hvis cache ikke finner skjemaet`() {
		// Gitt
		val cachetSkjema = SkjemaOgVedleggsdata(skjemanummer = "NAV 123", tema = "TEMA")
		hentSkjemaDataConsumer.cache.put("sanityList", listOf(cachetSkjema))

		// Så
		assertThrows<BackendErrorException> {
			hentSkjemaDataConsumer.hentSkjemaEllerVedlegg("NAV 14-05.07")
		}
	}

	@Test
	fun `uses and caches bundled schema list when endpoint returns an empty list`() {
		val skjemaClient = mockk<SkjemaClient>()
		every { skjemaClient.hent() } returns emptyList()
		val consumer = HentSkjemaDataConsumer(skjemaClient)

		val first = consumer.hentSkjemaEllerVedlegg("N6")
		val second = consumer.hentSkjemaEllerVedlegg("N6")

		assertEquals("N6", first.skjemanummer)
		assertEquals("Annet", first.tittel)
		assertEquals(first, second)
		verify(exactly = 1) { skjemaClient.hent() }
	}

	@Test
	fun `keeps current schema list when a refresh returns an empty list`() {
		val skjemaClient = mockk<SkjemaClient>()
		val currentSchema = SkjemaOgVedleggsdata(skjemanummer = "CURRENT", tema = "TEMA")
		every { skjemaClient.hent() } returnsMany listOf(listOf(currentSchema), emptyList())
		val consumer = HentSkjemaDataConsumer(skjemaClient)

		assertEquals("CURRENT", consumer.hentSkjemaEllerVedlegg("CURRENT").skjemanummer)
		val refreshed = consumer.cache.refresh("sanityList").get()

		assertEquals(listOf(currentSchema), refreshed)
		assertEquals("CURRENT", consumer.hentSkjemaEllerVedlegg("CURRENT").skjemanummer)
		verify(exactly = 2) { skjemaClient.hent() }
	}

}
