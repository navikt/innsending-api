package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.antivirus.AntivirusInterface
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.pdfutilities.AntallSider
import no.nav.soknad.pdfutilities.Validerer
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service

@Service
class FilValidatorService(
	private val restConfig: RestConfig,
	private val antivirus: AntivirusInterface,
	private val innsenderMetrics: InnsenderMetrics
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun validerFil(fil: Resource, innsendingsId: String) {

		// Sjekk filnavn
		val fileName = fil.filename
		if (!fileName.isNullOrEmpty() && fileName.contains(".")) {
			val split = fileName.split(".")
			logger.info("$innsendingsId: Skal validere ${split[split.size - 1]}")
		}

		// Sjekk om filen eksisterer og er lesbar
		if (!fil.isReadable) throw IllegalActionException(
			message = "Opplasting feilet. Ingen fil opplastet",
			errorCode = ErrorCode.FILE_CANNOT_BE_READ
		)

		// Sjekk filstørrelse og filformat
		val opplastet = (fil as ByteArrayResource).byteArray
		Validerer().validerStorrelse(
			innsendingId = innsendingsId,
			alleredeOpplastet = 0,
			opplastet = opplastet.size.toLong(),
			max = restConfig.maxFileSize.toLong(),
			errorCode = ErrorCode.VEDLEGG_FILE_SIZE_SUM_TOO_LARGE
		)
		Validerer().validerStorrelse(
			innsendingId = innsendingsId,
			alleredeOpplastet = 0,
			opplastet = opplastet.size.toLong(),
			max = restConfig.maxFileSizeSum.toLong(),
			errorCode = ErrorCode.FILE_SIZE_SUM_TOO_LARGE
		)

		Validerer().validereFilformat(innsendingsId, opplastet, fileName)

		val antallSider = AntallSider().finnAntallSider(opplastet)
		Validerer().validereAntallSider(antallSider ?: 0, restConfig.maxNumberOfPages, opplastet)

		// Sjekk om filen inneholder virus
		// TODO: Fiks dette
//		if (!antivirus.scan(opplastet)) throw IllegalActionException(
//			message = "Opplasting feilet. Filen inneholder virus",
//			errorCode = ErrorCode.VIRUS_SCAN_FAILED
//		)

		innsenderMetrics.setFileSize(opplastet.size.toLong())
		innsenderMetrics.setFileNumberOfPages(antallSider?.toLong() ?: 0)
	}
}
