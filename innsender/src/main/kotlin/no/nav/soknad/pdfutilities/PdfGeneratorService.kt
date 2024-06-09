package no.nav.soknad.pdfutilities

import com.github.jknack.handlebars.Handlebars
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException


class PdfGeneratorService {
	private var handlebars: Handlebars = Handlebars()
	private val logger = LoggerFactory.getLogger(javaClass)

	fun genererKvitteringPdf(modell: KvitteringsPdfModel): ByteArray {

		try {
			// Generer html string
			val template = handlebars.compile("/pdf/kvittering").apply(modell)
			return produserPdf(template)
		} catch (e: IOException) {
			logger.error("Feiler ved PDF generering av kvittering: {}", e.message)
			throw e
		}

	}

	fun genererEttersendingForsidePdf(modell: EttersendingForsidePdfModel): ByteArray {

		try {
			// Generer html string
			val template = handlebars.compile("/pdf/forside-ettersending").apply(modell)
			return produserPdf(template)
		} catch (e: IOException) {
			logger.error("Feiler ved PDF generering av kvittering: {}", e.message)
			throw e
		}

	}

	// Generer PDF fra html string
	fun produserPdf(html: String): ByteArray {
		try {
			ByteArrayOutputStream().use { os ->
				val builder = PdfRendererBuilder()

				builder.useFastMode()
				builder.usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_1_B)

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
					}, "arial"
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
