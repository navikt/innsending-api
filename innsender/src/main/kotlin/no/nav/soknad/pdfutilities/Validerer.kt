package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.exceptions.IllegalActionException
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException


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
			logger.warn("$innsendingId: Ulovlig filstørrelse, Opplastede fil(er) $alleredeOpplastet + $opplastet er større enn maksimalt tillatt ${max * 1024 * 1024}")
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


}
