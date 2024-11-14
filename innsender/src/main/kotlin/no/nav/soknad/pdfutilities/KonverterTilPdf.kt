package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.pdfutilities.FiltypeSjekker.Companion.imageFileTypes
import no.nav.soknad.pdfutilities.FiltypeSjekker.Companion.officeFileTypes
import no.nav.soknad.pdfutilities.FiltypeSjekker.Companion.textTypes
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*


@Service
class KonverterTilPdf(
	private val pdfConverter: FileToPdfInterface
) : KonverterTilPdfInterface {

	private val logger = LoggerFactory.getLogger(KonverterTilPdf::class.java)

	override fun tilPdf(
		fil: ByteArray,
		soknad: DokumentSoknadDto,
		filtype: String,
		vedleggsTittel: String?
	): Pair<ByteArray, Int> {

		logger.info("Skal konvertere filtype=$filtype til PDF")

		if ("pdf".equals(filtype, ignoreCase = true)) {
			return checkAndFormatPDF(fil)
/*
		} else if (imageFileTypes.keys.contains(filtype)) {
			return convertImageToPDF(genererFilnavn(soknad, vedleggsTittel ?: "Annet", filtype), fil)
*/
		} else if (officeFileTypes.keys.contains(filtype) || imageFileTypes.keys.contains(filtype)) {
			return createPDFFromOfficeDoc(genererFilnavn(soknad, vedleggsTittel ?: "Annet", filtype), fil)
		} else if (textTypes.contains(filtype)) {
			return createPDFFromText(soknad, vedleggsTittel ?: "Annet", fil)
		}	else {
				throw IllegalActionException(
				message = "Ulovlig filformat. Kan ikke konvertere til PDF",
				errorCode = ErrorCode.NOT_SUPPORTED_FILE_FORMAT
			)
		}
	}

	private fun checkAndFormatPDF(fil: ByteArray): Pair<ByteArray, Int> {
		val antallSider = AntallSider().finnAntallSider(fil) ?: 0
		return Pair(CheckAndFormatPdf().flatUtPdf(fil, antallSider), antallSider) // Bare hvis inneholder formfields?
	}

	private fun convertImageToPDF(filNavn: String, fil: ByteArray): Pair<ByteArray, Int> {
		val pdf = pdfConverter.imageToPdf(filNavn, fil)
		val antallSider = AntallSider().finnAntallSider(pdf) ?: 0

		return Pair(pdf, antallSider)
	}

	private fun genererFilnavn(soknad: DokumentSoknadDto, tittel: String, filtype:String): String {
		val generertFilnavn = soknad.innsendingsId + "-" + UUID.randomUUID().toString() + filtype
		logger.info("${soknad.innsendingsId}: Skal konvertere $filtype til vedlegg ${tittel ?: "annet"}, med filnavn=$generertFilnavn")
		return generertFilnavn
	}

	private fun createPDFFromText(soknad: DokumentSoknadDto, tittel: String?, text: ByteArray): Pair<ByteArray, Int> {
		val textString = FiltypeSjekker().charsetDetectAndReturnString(text)

		val pdf = PdfGenerator().lagPdfFraTekstFil(
			soknad,
			vedleggsTittel = tittel ?: "Annet",
			text = textString
		)
		val antallSider = AntallSider().finnAntallSider(pdf) ?: 0
		return Pair(pdf, antallSider)
	}

	private fun createPDFFromOfficeDoc(filnavn: String, fil: ByteArray): Pair<ByteArray, Int> {
		val pdf = pdfConverter.toPdf(filnavn, fil)
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
