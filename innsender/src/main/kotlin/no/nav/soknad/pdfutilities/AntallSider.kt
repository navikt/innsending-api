package no.nav.soknad.pdfutilities

import org.apache.pdfbox.Loader
import org.slf4j.LoggerFactory
import java.io.IOException

class AntallSider {
	private val logger = LoggerFactory.getLogger(javaClass)

	fun finnAntallSider(bytes: ByteArray?): Int? {
		if (bytes == null) return null
		if (bytes.isEmpty()) return 0
		if (FiltypeSjekker().isPdf(bytes)) {
			try {
				Loader.loadPDF(bytes).use { document ->
					return document.numberOfPages
				}
			} catch (e: IOException) {
				logger.error("Klarer ikke å åpne PDF for å kunne skjekke antall sider")
				throw RuntimeException("Klarer ikke å åpne PDF for å kunne skjekke antall sider")
			}
		} else {
			return 1
		}

	}

}
