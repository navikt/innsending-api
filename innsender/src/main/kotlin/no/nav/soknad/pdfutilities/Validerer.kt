package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.pdfutilities.FiltypeSjekker.Companion.supportedFileTypes
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdfwriter.compress.CompressParameters
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.preflight.ValidationResult
import org.apache.pdfbox.preflight.exception.SyntaxValidationException
import org.apache.pdfbox.preflight.parser.PreflightParser
import org.apache.tika.mime.MimeType
import org.apache.tika.mime.MimeTypes
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*


class Validerer {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun validereFilformat(innsendingId: String, file: ByteArray, fileName: String?): String {
		if (isPDF(file)) {
			// Kontroller at PDF er lovlig, dvs. ikke encrypted og passordbeskyttet
			erGyldigPdf(innsendingId, file)
			return "pdf"

		} else {
			val ext = getFileExtention(fileName)
			if (!supportedFileTypes.contains(ext)) {
				ulovligFilFormat(innsendingId, ext, file)
			}
			val primaryExtension: String = try {
					val fileType_full = FiltypeSjekker().detectContentType(file, fileName)
					val filtype = fileType_full.substringBefore(";")
					val allTypes = MimeTypes.getDefaultMimeTypes()
					val type: MimeType = allTypes.forName(filtype)
					type.getExtension()
				} catch (e: Exception) {
					logger.warn("Error checking filecontent for $ext. Will continue processing file", e)
					ext
				}

				if (!primaryExtension.equals(ext, true) ) {
					logger.warn("Mismatch specified fileextension $ext and detected filecontent $primaryExtension. Rejecting upload")
					ulovligFilFormat(innsendingId, fileName, file)
				}
				return primaryExtension
		}
	}

	private fun getFileExtention(fileName: String?): String {
		if (fileName == null) return "unknown"
		val tmp = File(fileName).extension.lowercase()
		return "." + if (tmp == "jpeg") "jpg" else if (tmp == "tif") "tiff" else tmp
	}

	private fun ulovligFilFormat(innsendingId: String, extention: String? = "", file: ByteArray) {
		logger.warn("$innsendingId: Ugyldig filtype for opplasting. Filextention: ${extention}, og filstart = ${if (file.size >= 4) (file[0] + file[1] + file[2] + file[3]) else file[0]}\")")
		throw IllegalActionException(
			message = "$innsendingId: Ugyldig filtype for opplasting. Kan kun laste opp filer av type ${supportedFileTypes.joinToString(", ")}",
			errorCode = ErrorCode.NOT_SUPPORTED_FILE_FORMAT
		)

	}

	fun validereAntallSider(antallSider: Int, maxAntallSider: Int = 200) {
		logger.info("Sjekke at $antallSider < $maxAntallSider")
		if (antallSider > maxAntallSider) {
			logger.warn("Opplastet fil med $antallSider sider overskrider $maxAntallSider")
			throw IllegalActionException(
				message = "For mange sider i fil. Opplastet fil med $antallSider sider som overskrider $maxAntallSider",
				errorCode = ErrorCode.FILE_WITH_TOO_TO_MANY_PAGES
			)
		}
	}

	fun validerStorrelse(
		innsendingId: String,
		alleredeOpplastet: Long,
		opplastet: Long,
		max: Long,
		errorCode: ErrorCode
	) {
		if (alleredeOpplastet + opplastet > max * 1024 * 1024) {
			logger.warn("$innsendingId: Ulovlig filstørrelse, Opplastede fil(er) $alleredeOpplastet + $opplastet er større enn maksimalt tillatt ${max * 1024 * 1024}. ErrorCode=$errorCode")
			throw IllegalActionException(
				message = "$innsendingId: Ulovlig filstørrelse. Opplastede fil(er) $alleredeOpplastet + $opplastet er større enn maksimalt tillatt ${max * 1024 * 1024}",
				errorCode = errorCode
			)
		}
	}

	private fun erGyldigPdf(innsendingId: String, input: ByteArray?) {
		try {
			Loader.loadPDF(input).use { document ->
				erGyldigPdDocument(innsendingId, document)
			}
		} catch (invalidPasswordException: InvalidPasswordException) {
			logger.warn(
				"$innsendingId: Opplasting av vedlegg feilet da PDF er kryptert, ${invalidPasswordException.message}",
				invalidPasswordException
			)
			throw IllegalActionException(
				message = "Opplastet fil er ikke lesbar. Kan ikke laste opp kryptert fil",
				errorCode = ErrorCode.FILE_CANNOT_BE_READ
			)
		} catch (ex: Exception) {
			if ("Kan ikke laste opp kryptert fil" == ex.message) {
				logger.warn("$innsendingId: Opplasting av vedlegg feilet da PDF er kryptert, ${ex.message}", ex)
			} else {
				logger.warn("$innsendingId: Opplasting av vedlegg feilet av ukjent årsak, ${ex.message}", ex)
			}
			throw IllegalActionException(
				message = "Lesing av filen feilet. Opplastet fil er ikke lesbar",
				errorCode = ErrorCode.FILE_CANNOT_BE_READ
			)
		}
	}

	private fun erGyldigPdDocument(innsendingId: String, document: PDDocument) {
		if (document.isEncrypted) {
			logger.warn("$innsendingId: Opplasting av vedlegg feilet da PDF er kryptert")
			throw RuntimeException("Kan ikke laste opp kryptert fil")
		}
	}

	private fun isPng(bytes: ByteArray): Boolean {
		return FiltypeSjekker().isPng(bytes)
	}

	private fun isPDF(bytes: ByteArray): Boolean {
		return FiltypeSjekker().isPdf(bytes)
	}

	private fun isImage(bytes: ByteArray): Boolean {
		return FiltypeSjekker().isImage(bytes)
	}

	private fun isDocx(bytes: ByteArray): Boolean {
		return FiltypeSjekker().isDocx(bytes)
	}

	private fun isText(bytes: ByteArray): Boolean {
		return FiltypeSjekker().isPlainText(bytes)
	}

	fun isPDFa(bytes: ByteArray): Boolean {
		var result: ValidationResult? = null
		var document: PDDocument? = null
		var file: File? = null
		val fileName = "tmp_${UUID.randomUUID()}"

		try {
			file = File.createTempFile(fileName, ".pdf")
			document = Loader.loadPDF(bytes)
			document.save(file, CompressParameters.NO_COMPRESSION)
			result = PreflightParser.validate(file)
			logger.info("PDF/A resultat: ${result.isValid}")

			return result?.isValid == true
		} catch (ex: SyntaxValidationException) {
			logger.warn("Klarte ikke å lese fil for å sjekke om gyldig PDF/a, ${ex.message}", ex)
			if (result != null) {
				val sb = StringBuilder()
				for (error in result.errorsList) {
					sb.append(error.errorCode + " : " + error.details + "\n")
				}
				logger.error("Feil liste:\n$sb")
			}
		} catch (ex: Error) {
			logger.warn("Klarte ikke å lese fil for å sjekke om gyldig PDF/a, ${ex.message}", ex)
		} finally {
			file?.deleteOnExit()
			file?.delete()
			document?.close()
		}

		return false
	}

}
