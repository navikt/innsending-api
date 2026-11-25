package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.embedded.Gotenberg
import no.nav.soknad.pdfutilities.gotenberg.GotenbergClientConfig
import no.nav.soknad.pdfutilities.gotenberg.GotenbergConvertToPdf

/**
 * PDF tests that only need Gotenberg, without a Spring context.
 * They use the same Gotenberg container as the Spring contexts. It starts on first use and stops when the JVM exits.
 */
object GotenbergTestConverters {

	private val converter by lazy { GotenbergConvertToPdf(GotenbergClientConfig().getGotenbergClientEmbedded(Gotenberg.shared)) }

	val pdfMerger: PdfMerger by lazy { PdfMerger(converter) }

	val konverterTilPdf: KonverterTilPdf by lazy { KonverterTilPdf(converter, pdfMerger) }
}
