package no.nav.soknad.innsending.consumerapis.skjema

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

}
