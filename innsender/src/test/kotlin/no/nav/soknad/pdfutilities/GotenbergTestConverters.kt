package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.embedded.Gotenberg
import no.nav.soknad.pdfutilities.gotenberg.GotenbergClientConfig
import no.nav.soknad.pdfutilities.gotenberg.GotenbergConvertToPdf

/**
 * PDF tests that only need Gotenberg, without a Spring context.
 * The container starts on first use and is shared by every test class in the JVM. Testcontainers stops it when the JVM exits.
 */
object GotenbergTestConverters {

	private val container: Gotenberg.Container by lazy { Gotenberg.Container().apply { start() } }

	private val converter by lazy { GotenbergConvertToPdf(GotenbergClientConfig().getGotenbergClientEmbedded(container)) }

	val pdfMerger: PdfMerger by lazy { PdfMerger(converter) }

	val konverterTilPdf: KonverterTilPdf by lazy { KonverterTilPdf(converter, pdfMerger) }
}
