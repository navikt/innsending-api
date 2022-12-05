package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.exceptions.IllegalActionException
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.preflight.PreflightDocument
import org.apache.pdfbox.preflight.ValidationResult
import org.apache.pdfbox.preflight.exception.SyntaxValidationException
import org.apache.pdfbox.preflight.parser.PreflightParser
import org.apache.pdfbox.preflight.utils.ByteArrayDataSource
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStreamWriter
import javax.print.attribute.standard.OutputDeviceAssigned


class Validerer() {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun validereFilformat(innsendingId: String, files: List<ByteArray>) {
		files.forEach { kontroller(innsendingId, it) }
	}

	private fun kontroller(innsendingId: String, file: ByteArray) {
		if (isPDF(file)) {
			// Kontroller at PDF er lovlig, dvs. ikke encrypted og passordbeskyttet
			erGyldigPdf(innsendingId, file)
		} else if (!isImage(file)) {
			logger.error("$innsendingId: Ugylding filtype for opplasting. Filstart = ${if (file.size>=4) (file[0] + file[1] + file[3] + file[4]) else file[0]}")
			throw IllegalActionException("$innsendingId: Ugyldig filtype for opplasting", "Kan kun laste opp filer av type PDF, JPEG, PNG og IMG", "errorCode.illegalAction.notSupportedFileFormat"
			)
		}
	}

	fun validerStorrelse(innsendingId: String, alleredeOpplastet: Long, opplastet: Long, max: Long, errorCode: String) {
		if (alleredeOpplastet + opplastet > max*1024*1024) {
			logger.warn("$innsendingId: Ulovlig filstørrelse, Opplastede fil(er) $alleredeOpplastet + $opplastet er større enn maksimalt tillatt ${max * 1024 * 1024}. ErrorCode=$errorCode")
			throw IllegalActionException("$innsendingId: Ulovlig filstørrelse", "Opplastede fil(er) $alleredeOpplastet + $opplastet er større enn maksimalt tillatt ${max*1024*1024}", errorCode)
		}
	}

	private fun erGyldigPdf(innsendingId: String, input: ByteArray?) {
		try {
			ByteArrayInputStream(input).use { bais ->
				PDDocument.load(bais).use { document -> erGyldigPdDocument(innsendingId, document) }
			}
		} catch (ex: InvalidPasswordException) {
			logger.error("$innsendingId: Opplasting av vedlegg feilet da PDF er kryptert, ${ex.message}")
			throw IllegalActionException("Opplastet fil er ikke lesbar", "Kan ikke laste opp kryptert fil", "errorCode.illegalAction.fileCannotBeRead")
		} catch (ex2: Exception) {
			logger.error("$innsendingId: Opplasting av vedlegg feilet av ukjent årsak, ${ex2.message}")
			throw IllegalActionException("Opplastet fil er ikke lesbar", "Lesing av filen feilet", "errorCode.illegalAction.fileCannotBeRead")
		}
	}

	private fun erGyldigPdDocument(innsendingId: String, document: PDDocument) {
		if (document.isEncrypted()) {
			logger.error("$innsendingId: Opplasting av vedlegg feilet da PDF er kryptert")
			throw IllegalActionException("Opplastet fil er ikke lesbar", "Kan ikke laste opp kryptert fil", "errorCode.illegalAction.fileCannotBeRead")
		}
	}

	fun isPng(bytes: ByteArray): Boolean {
		return FiltypeSjekker().isPng(bytes)
	}

	fun isPDF(bytes: ByteArray): Boolean {
		return FiltypeSjekker().isPdf(bytes)
	}

	fun isImage(bytes: ByteArray): Boolean {
		return FiltypeSjekker().isImage(bytes)
	}

	fun isPDFa(bytes: ByteArray): Boolean {
		ByteArrayInputStream(bytes).use { byteArrayInputStream ->
			var result: ValidationResult? = null
			var document: PreflightDocument? = null
			try {
				val stream = ByteArrayDataSource(ByteArrayInputStream(bytes))
				val parser = PreflightParser(stream)
				parser.parse()
				document = parser.preflightDocument
				document.validate()
				result = document.result
				return result.isValid
			} catch (ex: SyntaxValidationException) {
				logger.error("Klarte ikke å lese fil for å sjekke om gyldig PDF/a, ${ex.message}")
				if (result != null) {
					val sb = StringBuilder()
					for (error in result.errorsList) {
						sb.append(error.errorCode + " : " + error.details + "\n")
					}
					logger.error("Feil liste:\n"+sb.toString())
				}
			} catch (ex: Error) {
				logger.error("Klarte ikke å lese fil for å sjekke om gyldig PDF/a, ${ex.message}")
			}	finally {
				if (document != null) {
					document.close()
				}
			}
		}
		return false
	}

}
