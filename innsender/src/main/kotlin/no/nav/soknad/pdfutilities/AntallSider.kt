package no.nav.soknad.pdfutilities

import org.apache.pdfbox.pdmodel.PDDocument
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.IOException

class AntallSider {
	private val logger = LoggerFactory.getLogger(javaClass)

	fun finnAntallSider(bytes: ByteArray?): Int {
		try {
			ByteArrayInputStream(bytes).use { stream ->
				PDDocument.load(stream).use { document -> return document.numberOfPages }
			}
		} catch (e: IOException) {
			logger.error("Klarer ikke å åpne PDF for å kunne skjekke antall sider")
			throw RuntimeException("Klarer ikke å åpne PDF for å kunne skjekke antall sider")
		}
	}

}
