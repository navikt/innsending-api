package no.nav.soknad.innsending.service

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File


class PdfGeneratorService {
	private val logger = LoggerFactory.getLogger(javaClass)

	// Generer PDF fra html string
	fun generatePdf(html: String): ByteArray {
		try {
			ByteArrayOutputStream().use { os ->
				val builder = PdfRendererBuilder()

				builder.useFastMode()
				builder.usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_1_A)

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
