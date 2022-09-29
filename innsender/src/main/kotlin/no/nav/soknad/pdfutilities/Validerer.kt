package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.exceptions.IllegalActionException
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import org.apache.pdfbox.pdmodel.PDDocument


class Validerer() {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun validereFilformat(files: List<ByteArray>) {
		files.forEach { kontroller(it) }
	}

	fun kontroller(file: ByteArray) {
		if (isPDF(file)) {
			// Kontroller at PDF er lovlig, dvs. ikke encrypted og passordbeskyttet
			try {
				erGyldig(file)
			} catch (e: Exception) {
				throw IllegalActionException("Opplastet fil er ikke lesbar", "Kan ikke laste opp kryptert fil"
				)
			}
		} else if (!isImage(file)) {
			throw IllegalActionException("Ugyldig filtype for opplasting", "Kan kun laste opp filer av type PDF, PNG og IMG"
			)
		}
	}

	fun validerStorrelse(opplastet: Long, max: Long) {
		if (opplastet > max*1024*1024) {
			throw IllegalActionException("Ulovlig filstørrelse", "Opplastede fil(er) er større enn maksimalt tillatt")
		}
	}

	private fun erGyldig(input: ByteArray?) {
		try {
			ByteArrayInputStream(input).use { bais -> PDDocument.load(bais).use { document -> erGyldigPdDocument(document) } }
		} catch (e: java.lang.Exception) {
			logger.error(
				"Klarte ikke å sjekke om vedlegget er gyldig {}",	e.message
			)
			throw IllegalActionException("Ukjent filtype", "Klarte ikke å sjekke om vedlegget er gyldig")
		}
	}

	private fun erGyldigPdDocument(document: PDDocument) {
		if (document.isEncrypted()) {
			logger.error("Opplasting av vedlegg feilet da PDF er kryptert")
			throw IllegalActionException("Opplastet fil er ikke lesbar", "Kan ikke laste opp kryptert fil")
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
