package no.nav.soknad.pdfutilities

import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.io.RandomAccessRead
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.function.Consumer

@Service
class PdfMerger(
	private val pdfConverter: FileToPdfInterface
) : PdfMergerInterface {
	private val logger = LoggerFactory.getLogger(javaClass)

	override fun mergePdfer(docs: List<ByteArray>): ByteArray {
		if (docs.size == 1) return docs[0]
		return try {
			mergeWithGotenberg(docs)
		} catch (e: Exception) {
			try {
				mergeWithPDFBox(docs)
			} catch (ex: Exception) {
				logger.error("Merging av filer feilet")
				throw ex
			}
		}
	}

	private fun mergeWithGotenberg(docs: List<ByteArray>): ByteArray {
		logger.info("Merging av filer v.hj.a. Gotenberg")
		try {
			return pdfConverter.mergePdfs("mergedFile", docs)
		} catch (ex: Exception) {
			logger.warn("Merge av PDF dokumenter i Gotenberg feilet", ex)
			throw ex
		}
	}

	override fun mergeWithPDFBox(docs: List<ByteArray>): ByteArray{
		val randomAccess = mutableListOf<RandomAccessRead>()
		return try {
			for (bytes in docs) {
				randomAccess.add(RandomAccessReadBuffer(bytes))
			}
			mergePdfStreams(randomAccess)
		} catch (ex: Exception) {
			logger.warn("Merging av filer med PDFBox feilet", ex)
			throw ex
		} finally {
			randomAccess.forEach(Consumer { i: RandomAccessRead ->
				try {
					i.close()
				} catch (e: IOException) {
					logger.error("Opprydding etter merging av PDFer med PDFBox feilet", e)
				}
			}
			)
		}
	}


	override fun setPdfMetadata(file: ByteArray, title: String, language: String?): ByteArray {
		var pdfDocument: PDDocument? = null
		val out = ByteArrayOutputStream()
		try {
			pdfDocument = Loader.loadPDF(file)
			val docInfo = pdfDocument.documentInformation
			val docCatalog = pdfDocument.documentCatalog
			docInfo.title = title
			if (language != null) {
				docCatalog.language = fixLanguage(language)
			}
			pdfDocument.save(out)
			return out.toByteArray()
		} finally {
			out.close()
			if (pdfDocument != null) {
				pdfDocument.close()
			}
		}
	}

	private fun fixLanguage(language: String): String {
		if(language.length > 2) return language
		else if (language.startsWith("en")) {return "English"}
		else if (language.startsWith("no") || language.startsWith("nn") || language.startsWith("nb")) {return "Norwegian"}
		else return "Norwegian"
	}

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

}
