package no.nav.soknad.pdfutilities

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
				throw Exception("Kan ikke laste opp kryptert fil", e
				)
			}
		} else if (!isImage(file)) {
			throw Exception("Ugyldig filtype for opplasting"
			)
		}
	}

	fun validerStorrelse(files: List<ByteArray>, max: Int) {
		var storrelse = 0
		files.forEach {storrelse += it.size}
		if (storrelse > max) {
			throw Exception("Opplastede fil(er) er større enn maksimalt tillatt")
		}
	}

	private fun erGyldig(input: ByteArray?) {
		try {
			ByteArrayInputStream(input).use { bais -> PDDocument.load(bais).use { document -> erGyldigPdDocument(document) } }
		} catch (e: java.lang.Exception) {
			logger.error(
				"Klarte ikke å sjekke om vedlegget er gyldig {}",	e.message
			)
			throw RuntimeException("Klarte ikke å sjekke om vedlegget er gyldig")
		}
	}

	private fun erGyldigPdDocument(document: PDDocument) {
		if (document.isEncrypted()) {
			logger.error("Opplasting av vedlegg feilet da PDF er kryptert")
			throw RuntimeException("opplasting.feilmelding.pdf.kryptert")
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
