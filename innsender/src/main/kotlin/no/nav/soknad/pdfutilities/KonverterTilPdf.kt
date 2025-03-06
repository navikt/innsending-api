package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.pdfutilities.FiltypeSjekker.Companion.imageFileTypes
import no.nav.soknad.pdfutilities.FiltypeSjekker.Companion.officeFileTypes
import no.nav.soknad.pdfutilities.FiltypeSjekker.Companion.textTypes
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.util.*


@Service
class KonverterTilPdf(
	private val pdfConverter: FileToPdfInterface,
	private val pdfMerger: PdfMerger
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
		} else if (officeFileTypes.keys.contains(filtype) || imageFileTypes.keys.contains(filtype)) {
			val tittel = vedleggsTittel ?: "Annet"
			return createPDFFromOfficeDoc(genererFilnavn(soknad, tittel, filtype), fil, tittel, soknad.spraak ?: "nb-NO")
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
		return Pair(CheckAndFormatPdf().flatUtPdf(pdfMerger, fil, antallSider), antallSider) // Bare hvis inneholder formfields?
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

	private fun createPDFFromOfficeDoc(filnavn: String, fil: ByteArray, tittel: String = "Annet", spraak: String = "nb-NO"): Pair<ByteArray, Int> {
		val pdf = pdfConverter.toPdf(filnavn, fil)
		val antallSider = AntallSider().finnAntallSider(pdf) ?: 0
		return Pair(setPdfMetadata(pdf, tittel, spraak), antallSider)
	}

	override fun flatUtPdf(fil: ByteArray, antallSider: Int): ByteArray {
		return CheckAndFormatPdf().flatUtPdf(pdfMerger, fil, antallSider)
	}

	override fun harSkrivbareFelt(input: ByteArray?): Boolean {
		return CheckAndFormatPdf().harSkrivbareFelt(input)
	}

	// Using libreOffice route, metadata for language and title is not set
	private fun setPdfMetadata(file: ByteArray, title: String, language: String): ByteArray {
		var pdfDocument: PDDocument? = null
		val out = ByteArrayOutputStream()
		try {
			pdfDocument = Loader.loadPDF(file)
			val docInfo = pdfDocument.documentInformation
			val docCatalog = pdfDocument.documentCatalog
			docInfo.title = title
			docCatalog.language = fixLanguage(if (language == "en-UK") "English" else "Norwegian")
			pdfDocument.save(out)
			return out.toByteArray()
		} finally {
				out.close()
		    if (pdfDocument != null) {
					pdfDocument.close()
				}
		}
	}

	private fun fixLanguage(language: String): String {
		if(language.length > 2) return language
		else if (language.startsWith("en")) {return language.substring(0,2)+"-UK"}
		else if (language.startsWith("no") || language.startsWith("nn") || language.startsWith("nb")) {return language.substring(0,2)+"-NO"}
		else return "en-UK"
	}
}
