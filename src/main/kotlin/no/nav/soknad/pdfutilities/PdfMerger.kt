package no.nav.soknad.pdfutilities

import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.function.Consumer

class PdfMerger {

	private val logger = LoggerFactory.getLogger(javaClass)

	private fun mergePdfStreams(docs: List<InputStream>): ByteArray {
		try {
			ByteArrayOutputStream().use { out ->
				val merger = PDFMergerUtility()
				merger.destinationStream = out
				merger.addSources(docs)
				merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
				return (merger.destinationStream as ByteArrayOutputStream).toByteArray()
			}
		} catch (e: IOException) {
			logger.error("Merge av PDF dokumenter feilet")
			throw RuntimeException("Merge av PDF dokumenter feilet")
		}
	}

	fun mergePdfer(docs: List<ByteArray>): ByteArray {
		val streams = mutableListOf<InputStream>()
		return try {
			for (bytes in docs) {
				streams.add(ByteArrayInputStream(bytes))
			}
			mergePdfStreams(streams)
		} finally {
			streams.forEach(Consumer { i: InputStream ->
				try {
					i.close()
				} catch (e: IOException) {
					logger.error("Opprydding etter merging av PDFer feilet")
				}
			})
		}
	}

}
