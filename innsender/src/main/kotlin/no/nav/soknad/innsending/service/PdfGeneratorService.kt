package no.nav.soknad.innsending.service

import com.github.jknack.handlebars.Handlebars
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder.CacheStore
import no.nav.soknad.innsending.model.PdfData
import no.nav.soknad.innsending.model.PdfDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

data class HTMLInput(
	val content: String,
	val date: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd. MMMM yyyy, 'kl' HH.mm")),
	val language: String,
	val title: String,
	val fnr: String,
	val skjemanummer: String,
	val githash: String
)

@Service
class PdfGeneratorService {
	private val logger = LoggerFactory.getLogger(javaClass)
	private var handlebars: Handlebars = Handlebars()
	
	fun ByteArray.toBase64(): String =
		String(Base64.getEncoder().encode(this))

	fun generatePdfDtoFromData(pdfData: PdfData): PdfDto {
		if (pdfData.base64Html == null) throw IllegalArgumentException("HTML input (base64) is null")

		val html = Base64.getDecoder().decode(pdfData.base64Html).toString(Charsets.UTF_8)

		val htmlInput =
			HTMLInput(
				content = html,
				language = pdfData.spraakkode ?: "nb-NO",
				title = pdfData.dokumentTittel ?: "",
				fnr = pdfData.fnr ?: "",
				skjemanummer = pdfData.skjemanummer ?: "",
				githash = pdfData.skjemaversjon ?: ""
			)

		val template = handlebars.compile("pdf/base").apply(htmlInput)

		val pdfByteArray = generatePdf(template)
		return PdfDto(pdfByteArray.toBase64())
	}


	// Generate PDF from HTML string
	fun generatePdf(html: String): ByteArray {
		try {
			print(CacheStore.entries.toTypedArray())
			ByteArrayOutputStream().use { os ->
				val builder = PdfRendererBuilder()

				builder.useFastMode()
				builder.usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_1_A)
				builder.usePdfUaAccessbility(true)

				// Fargeprofil, må være byte array
				builder.useColorProfile(
					PdfGeneratorService::class.java.getResource("/pdf/fonts/icc/sRGB.icc")?.readBytes()
				)

				// Normal og Bold fonts, må være filer
				builder.useFont(
					PdfGeneratorService::class.java.getResource("/pdf/fonts/arial/arial.ttf")?.path?.let {
						File(it)
					}, "arial"
				)
				builder.useFont(
					PdfGeneratorService::class.java.getResource("/pdf/fonts/arial/arialbd.ttf")?.path?.let {
						File(it)
					}, "arial", 600, BaseRendererBuilder.FontStyle.NORMAL, true
				)

				// Mappe for html filer (som fonter og bilder), må være string uri
				builder.withHtmlContent(
					html,
					PdfGeneratorService::class.java.getResource("/pdf/")?.toExternalForm() ?: ""
				)

				builder.toStream(os)
				builder.run()

				return os.toByteArray()
			}
		} catch (e: Exception) {
			logger.error("Feiler i pdf-fil-generering", e)
			throw e
		}
	}


}
