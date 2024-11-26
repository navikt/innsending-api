package no.nav.soknad.pdfutilities

import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.io.RandomAccessRead
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.function.Consumer

class PdfMerger {

	private val logger = LoggerFactory.getLogger(javaClass)

	private fun mergePdfStreams(docs: List<RandomAccessRead>): ByteArray {
		try {
			ByteArrayOutputStream().use { out ->
				val merger = PDFMergerUtility()
				merger.destinationStream = out
				merger.addSources(docs)
				merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly().streamCache)
				return (merger.destinationStream as ByteArrayOutputStream).toByteArray()
			}
		} catch (e: IOException) {
			logger.error("Merge av PDF dokumenter feilet", e)
			throw RuntimeException("Merge av PDF dokumenter feilet", e)
		}
	}

	fun mergePdfer(docs: List<ByteArray>): ByteArray {
		val randomAccess = mutableListOf<RandomAccessRead>()
		return try {
			for (bytes in docs) {
				randomAccess.add(RandomAccessReadBuffer(bytes))
			}
			mergePdfStreams(randomAccess)
		} finally {
			randomAccess.forEach(Consumer { i: RandomAccessRead ->
				try {
					i.close()
				} catch (e: IOException) {
					logger.error("Opprydding etter merging av PDFer feilet")
				}
			})
		}
	}

}
