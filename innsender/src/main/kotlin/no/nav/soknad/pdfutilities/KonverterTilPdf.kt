package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.DokumentSoknadDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class KonverterTilPdf(
	private val docxConverter: DocxToPdfInterface
) : KonverterTilPdfInterface {

	private val logger = LoggerFactory.getLogger(KonverterTilPdf::class.java)

	override fun tilPdf(
		fil: ByteArray,
		soknad: DokumentSoknadDto,
		sammensattNavn: String?,
		vedleggsTittel: String?
	): Pair<ByteArray, Int> {

		val filtype_full = FiltypeSjekker().detectContentType(fil, null)
		logger.info("Skal konvertere filtype=$filtype_full til PDF")
		val filtype = filtype_full.substringBefore(";")

		when (filtype) {
			"application/pdf" -> return checkAndFormatPDF(fil)
			"image/png", "image/jpeg" -> return ConvertImageToPdf().pdfFromImage(fil)
			"text/plain" -> return createPDFFromText(soknad, vedleggsTittel ?: "Annet", fil)
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> return createPDFFromWord(
				soknad,
				vedleggsTittel,
				fil
			)

			else -> throw IllegalActionException(
				message = "Ulovlig filformat. Kan ikke konvertere til PDF",
				errorCode = ErrorCode.NOT_SUPPORTED_FILE_FORMAT
			)
		}
		/*
				if (filtype.equals("application/pdf")) return checkAndFormatPDF(fil)
				if (filtype.equals("image/png") || filtype.equals("image/jpeg")) return ConvertImageToPdf().pdfFromImage(fil)

				if (filtype.equals("text/plain")) return createPDFFromText(soknad, vedleggsTittel ?: "Annet", fil)
				if (filtype.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
					return createPDFFromWord(soknad, vedleggsTittel, fil)

				throw IllegalActionException(
					message = "Ulovlig filformat. Kan ikke konvertere til PDF",
					errorCode = ErrorCode.NOT_SUPPORTED_FILE_FORMAT
				)
		*/
	}

	private fun checkAndFormatPDF(fil: ByteArray): Pair<ByteArray, Int> {
		val antallSider = AntallSider().finnAntallSider(fil) ?: 0
		return Pair(CheckAndFormatPdf().flatUtPdf(fil, antallSider), antallSider) // Bare hvis inneholder formfields?
	}

	private fun createPDFFromText(soknad: DokumentSoknadDto, tittel: String?, text: ByteArray): Pair<ByteArray, Int> {
		val pdf = PdfGenerator().lagPdfFraTekstFil(
			soknad,
			vedleggsTittel = tittel ?: "Annet",
			text = text.decodeToString()
		)
		val antallSider = AntallSider().finnAntallSider(pdf) ?: 0
		return Pair(pdf, antallSider)
	}

	private fun createPDFFromWord(soknad: DokumentSoknadDto, tittel: String?, fil: ByteArray): Pair<ByteArray, Int> {
		val pdf = docxConverter.toPdf(soknad.innsendingsId + "-" + (tittel ?: "annet") + ".docx", fil)
		val antallSider = AntallSider().finnAntallSider(pdf) ?: 0
		return Pair(pdf, antallSider)
	}

	override fun flatUtPdf(fil: ByteArray, antallSider: Int): ByteArray {
		return CheckAndFormatPdf().flatUtPdf(fil, antallSider)
	}

	override fun harSkrivbareFelt(input: ByteArray?): Boolean {
		return CheckAndFormatPdf().harSkrivbareFelt(input)
	}

}
