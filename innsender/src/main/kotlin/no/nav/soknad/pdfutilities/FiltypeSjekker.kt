package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import org.apache.commons.lang3.ArrayUtils
import org.apache.tika.Tika
import org.apache.tika.exception.TikaException
import org.apache.tika.io.TikaInputStream
import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.parser.txt.CharsetDetector
import org.apache.tika.sax.BodyContentHandler
import org.slf4j.LoggerFactory
import org.xml.sax.SAXException
import java.nio.charset.Charset
import java.util.function.Predicate


class FiltypeSjekker {

	companion object {
		val officeFileTypes = mapOf(
			".docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
			".doc" to "application/msword",
			".odt" to "application/vnd.oasis.opendocument.text",
			".rtf" to "application/rtf"
		)
		val imageFileTypes = mapOf(
			".jpg" to "image/jpeg",
			".jpeg" to "image/jpeg",
			".png" to "image/png",
			".bmp" to "image/bmp",
			".tiff" to "image/tiff",
			".tif" to "image/tiff",
			".gif" to "image/gif"
		)
		val textTypes = mapOf("txt" to "text/plain")
		val supportedFileTypes = officeFileTypes.keys + imageFileTypes.keys + textTypes.keys

	}

	private val logger = LoggerFactory.getLogger(FiltypeSjekker::class.java)

	val IS_PNG =
		Predicate { bytes: ByteArray ->
			Tika().detect(
				ArrayUtils.subarray(
					bytes.clone(),
					0,
					2048
				)
			).equals("image/png", true)
		}
	val IS_PDF =
		Predicate { bytes: ByteArray? ->
			Tika().detect(bytes).equals("application/pdf", true)
		}
	val IS_JPG =
		Predicate { bytes: ByteArray? ->
			Tika().detect(bytes).equals("image/jpeg", true)
		}
	val IS_IMAGE =
		Predicate { bytes: ByteArray -> IS_PNG.test(bytes) || IS_JPG.test(bytes) }

	val IS_DOCX =
		Predicate { bytes: ByteArray ->
			detectContentType(bytes).equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", true)
				|| Tika().detect(bytes).equals("application/x-tika-ooxml", true)
		}

	val IS_TEXT =
		Predicate { bytes: ByteArray ->
			Tika().detect(bytes).equals("text/plain", true)
		}

	fun isPng(bytes: ByteArray): Boolean {
		return IS_PNG.test(bytes)
	}

	fun isPdf(bytes: ByteArray): Boolean {
		return IS_PDF.test(bytes)
	}

	fun isJpg(bytes: ByteArray): Boolean {
		return IS_JPG.test(bytes)
	}

	fun isImage(bytes: ByteArray): Boolean {
		return IS_IMAGE.test(bytes)
	}

	fun isDocx(bytes: ByteArray): Boolean {
		return IS_DOCX.test(bytes)
	}

	fun isPlainText(bytes: ByteArray): Boolean {
		return IS_TEXT.test(bytes)
	}

	fun detectContentType(bytes: ByteArray, fileName: String? = null): String {
		val metadata = Metadata()
		fileName?.let {
			metadata.add(Metadata.CONTENT_DISPOSITION, "attachment; filename=$it")
		}

		// Use AutoDetectParser for better MIME type detection
		val parser = AutoDetectParser()

		try {
			TikaInputStream.get(bytes.inputStream()).use { tikaInputStream ->
				// Create a content handler (no output needed here)
				val handler = BodyContentHandler(-1)

				// Parse the input and detect the content type
				parser.parse(tikaInputStream, handler, metadata)

				logger.info("DetectContentType: ${metadata.get(Metadata.CONTENT_TYPE)}")
				// Return the detected content type from the metadata
				return metadata.get(Metadata.CONTENT_TYPE) ?: "application/octet-stream"
			}

		} catch (e: SAXException) {
			logger.warn("SAX Error parsing document", e)
			throw IllegalActionException("Feil ved sjekking av filtype", e, ErrorCode.TYPE_DETECTION_OR_CONVERSION_ERROR)
		} catch (e: TikaException) {
			logger.warn("Tika Error detecting content type", e)
			throw IllegalActionException("Feil ved sjekking av filtype", e, ErrorCode.TYPE_DETECTION_OR_CONVERSION_ERROR)
		} catch (e: Exception) {
			logger.warn("Error detecting content type", e)
			throw IllegalActionException("Feil ved sjekking av filtype", e, ErrorCode.TYPE_DETECTION_OR_CONVERSION_ERROR)
		}

	}


	fun charsetDetectAndReturnString(bytes: ByteArray): String {
		val detector = CharsetDetector()
		detector.setText(bytes)
		val type = detector.detect()
		logger.debug("Detected charset: $type")
		if (!Charset.defaultCharset().name().equals(type.name, true)) {
			val tmpString = bytes.toString(Charset.forName(type.name))
			return tmpString.toByteArray(Charset.defaultCharset()).decodeToString()
		}

		return bytes.decodeToString()
	}

}
