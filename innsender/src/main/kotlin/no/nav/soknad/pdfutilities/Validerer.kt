package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.preflight.ValidationResult
import org.apache.pdfbox.preflight.exception.SyntaxValidationException
import org.apache.pdfbox.preflight.parser.PreflightParser
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*


class Validerer {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun validereFilformat(innsendingId: String, files: List<ByteArray>) {
		files.forEach { kontroller(innsendingId, it) }
	}

	fun validereFilformat(innsendingId: String, file: ByteArray, fileName: String?) {
		kontroller(innsendingId, file, fileName)
	}

	private fun kontroller(innsendingId: String, file: ByteArray, fileName: String? = "") {
		if (isPDF(file)) {
			// Kontroller at PDF er lovlig, dvs. ikke encrypted og passordbeskyttet
			erGyldigPdf(innsendingId, file)
		} else if (!isImage(file)) {
			logger.warn("$innsendingId: $fileName har ugylding filtype for opplasting. Filstart = ${if (file.size >= 4) (file[0] + file[1] + file[3] + file[4]) else file[0]}")
			throw IllegalActionException(
				message = "$innsendingId: Ugyldig filtype for opplasting. Kan kun laste opp filer av type PDF, JPEG, PNG og IMG",
				errorCode = ErrorCode.NOT_SUPPORTED_FILE_FORMAT
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
			logger.warn("$innsendingId: Opplasting av vedlegg feilet da PDF er kryptert, ${invalidPasswordException.message}")
			throw IllegalActionException(
				message = "Opplastet fil er ikke lesbar. Kan ikke laste opp kryptert fil",
				errorCode = ErrorCode.FILE_CANNOT_BE_READ
			)
		} catch (ex: Exception) {
			if ("Kan ikke laste opp kryptert fil" == ex.message) {
				logger.warn("$innsendingId: Opplasting av vedlegg feilet da PDF er kryptert, ${ex.message}")
			} else {
				logger.error("$innsendingId: Opplasting av vedlegg feilet av ukjent årsak, ${ex.message}")
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

	fun isPDFa(bytes: ByteArray): Boolean {
		var result: ValidationResult? = null
		var document: PDDocument? = null
		val fileName = "tmp_${UUID.randomUUID()}.pdf"
		val file = File(fileName)

		try {
			document = Loader.loadPDF(bytes)
			document.save(fileName)
			result = PreflightParser.validate(file)

			// FIXME: Fails with "/XRef cross reference streams are not allowed"

			return result?.isValid == true
		} catch (ex: SyntaxValidationException) {
			logger.warn("Klarte ikke å lese fil for å sjekke om gyldig PDF/a, ${ex.message}")
			if (result != null) {
				val sb = StringBuilder()
				for (error in result.errorsList) {
					sb.append(error.errorCode + " : " + error.details + "\n")
				}
				logger.error("Feil liste:\n$sb")
			}
		} catch (ex: Error) {
			logger.warn("Klarte ikke å lese fil for å sjekke om gyldig PDF/a, ${ex.message}")
		} finally {
			file.delete()
			document?.close()
		}

		return false
	}

}
