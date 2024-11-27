package no.nav.soknad.pdfutilities

import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.io.RandomAccessRead
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.function.Consumer

@Service
class PdfMerger(
	private val pdfConverter: FileToPdfInterface
) {
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
		if (docs.size == 1) return docs[0]
		val randomAccess = mutableListOf<RandomAccessRead>()
		return try {
			for (bytes in docs) {
				randomAccess.add(RandomAccessReadBuffer(bytes))
			}
			mergePdfStreams(randomAccess)
		} catch (e: Exception) {
			logger.warn("Merging av filer feilet, forsøker Gotenberg")
			try {
				pdfConverter.mergePdfs("mergedFile", docs)
			} catch (ex: Exception) {
				logger.error("Merge av PDF dokumenter i Gotenberg feilet")
				throw RuntimeException("Merge av PDF dokumenter i Gotenberg feilet", ex)
			}
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
