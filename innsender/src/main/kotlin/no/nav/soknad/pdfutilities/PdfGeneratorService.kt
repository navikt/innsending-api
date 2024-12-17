package no.nav.soknad.pdfutilities

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Helper
import com.github.jknack.handlebars.Options
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.openhtmltopdf.svgsupport.BatikSVGDrawer
import no.nav.soknad.pdfutilities.models.EttersendingForsidePdfModel
import no.nav.soknad.pdfutilities.models.KvitteringsPdfModel
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
			val template = handlebars.compile("/pdf/templates/kvittering").apply(modell)
			return produserPdf(template)
		} catch (e: IOException) {
			logger.error("Feiler ved PDF generering av kvittering: {}", e.message)
			throw e
		}

	}

	fun genererEttersendingForsidePdf(modell: EttersendingForsidePdfModel): ByteArray {

		try {
			// Generer html string
			val template = handlebars.compile("/pdf/templates/forside-ettersending").apply(modell)
			return produserPdf(template)
		} catch (e: IOException) {
			logger.error("Feiler ved PDF generering av kvittering: {}", e.message)
			throw e
		}

	}

	fun genererPdfFromText(modell: TextToPdfModel): ByteArray {

		try {
			//registerSplitTextHelper(handlebars)
			handlebars.registerHelpers(TextFormattingMethods())
			// Generer html string
			val template = handlebars.compile("/pdf/templates/text-to-pdf").apply(modell)
			return produserPdf(template)
		} catch (e: IOException) {
			logger.error("Feiler ved PDF generering fra tekst: {}", e.message)
			throw e
		}

	}

	// Generer PDF fra html string
	fun produserPdf(html: String): ByteArray {
		try {
			ByteArrayOutputStream().use { os ->
				val builder = PdfRendererBuilder()

				builder.useFastMode()
				builder.usePdfUaAccessbility(true)
				builder.useSVGDrawer(BatikSVGDrawer())
				builder.usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_2_A)

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

	fun registerSplitTextHelper(handlebars: Handlebars) {
		handlebars.registerHelper("split", object : Helper<String> {
			override fun apply(p0: String?, p1: Options?): Any {
				if (p0 != null) {
					return p0.split("\n")
				}
				return ""
			}
		})
	}

	fun registerTabHelper(handlebars: Handlebars) {
		handlebars.registerHelper("tab", object : Helper<String> {
			override fun apply(p0: String?, p1: Options?): Any {
				if (p0 != null) {
					return p0.replace("\t", "&nbsp;&nbsp;")
				}
				return ""
			}
		})
	}

}
