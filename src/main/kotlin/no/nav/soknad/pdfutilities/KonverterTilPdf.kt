package no.nav.soknad.pdfutilities

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode
import org.apache.pdfbox.pdmodel.common.PDMetadata
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.xmpbox.XMPMetadata
import org.apache.xmpbox.schema.DublinCoreSchema
import org.apache.xmpbox.schema.PDFAIdentificationSchema
import org.apache.xmpbox.type.BadFieldValueException
import org.apache.xmpbox.xml.XmpSerializer
import org.slf4j.LoggerFactory
import java.awt.Dimension
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.xml.transform.TransformerException

class KonverterTilPdf {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun tilPdf(fil: ByteArray): ByteArray {
		if (FiltypeSjekker().isPdf(fil)) {
			return flatUtPdf(fil)
		} else if (FiltypeSjekker().isImage(fil)) {
			return createPDFFromImage(fil)
		}
		throw Exception("Ulovlig filformat. Kan ikke konvertere til PDF")
	}

	fun flatUtPdf(fil: ByteArray): ByteArray {
		// Konvertere fra PDF til bilde og tilbake til PDF
		val images = KonverterTilPng().konverterTilPng(fil)
		val pdfList = mutableListOf<ByteArray>()
		for (i in 0..images.size-1) pdfList.add(createPDFFromImage(images[i]))
		return PdfMerger().mergePdfer(pdfList)
	}

	fun createPDFFromImage(image: ByteArray): ByteArray {
		try {
			PDDocument().use { doc ->
				val pdImage = PDImageXObject.createFromByteArray(doc, image, null)
				val scaledSize = getScaledDimension(pdImage.width, pdImage.height)
				val page = PDPage(PDRectangle(scaledSize.width.toFloat(), scaledSize.height.toFloat()))
				doc.addPage(page)
				PDPageContentStream(doc, page, AppendMode.APPEND, true, true).use { contentStream ->
					contentStream.drawImage(
						pdImage,
						0f,
						0f,
						scaledSize.width.toFloat(),
						scaledSize.height.toFloat()
					)
				}
				addFonts(doc)
				addDC(doc)
				ByteArrayOutputStream().use { byteArrayOutputStream ->
					doc.save(byteArrayOutputStream)
					return byteArrayOutputStream.toByteArray()
				}
			}
		} catch (ioe: IOException) {
			logger.error("Klarte ikke å sjekke filtype til PDF. Feil: '{}'", ioe.message)
			throw RuntimeException("vedlegg.opplasting.feil.generell")
		} catch (t: Throwable) {
			logger.error("Klarte ikke å sjekke filtype til PDF. Feil: '{}'", t)
			throw RuntimeException("vedlegg.opplasting.feil.generell")
		}
	}

	private fun addFonts(doc: PDDocument) {
		try {
			KonverterTilPdf::class.java.getResourceAsStream("/fonts/arial/ArialMT.ttf").use { fontis ->
				val font: PDFont = PDType0Font.load(doc, fontis)
				if (!font.isEmbedded) {
					logger.warn("Klarte ikke å laste default påkrevde fonter ved konvertering til PDF/A")
				}
			}
		} catch (e: IOException) {
			logger.error("Lasting av fonter ved konvertering til PDF/A feilet {}", e.message)
		}
	}

	private fun addDC(doc: PDDocument) {
		val xmp: XMPMetadata = XMPMetadata.createXMPMetadata()
		try {
			ByteArrayOutputStream().use { baos ->
				val dc: DublinCoreSchema = xmp.createAndAddDublinCoreSchema()
				dc.setTitle("image")
				val id: PDFAIdentificationSchema = xmp.createAndAddPFAIdentificationSchema()
				id.setPart(1)
				id.setConformance("B")
				val serializer = XmpSerializer()
				serializer.serialize(xmp, baos, true)
				val metadata = PDMetadata(doc)
				metadata.importXMPMetadata(baos.toByteArray())
				doc.documentCatalog.metadata = metadata
			}
		} catch (e: BadFieldValueException) {
			// won't happen here, as the provided value is valid
			throw IllegalArgumentException(e)
		} catch (ioe: IOException) {
			logger.error(
				"Feil ved lasting av XMPMetadata ved konvertering av Image til PDF/A, {}",
				ioe.message
			)
		} catch (ioe: TransformerException) {
			logger.error(
				"Feil ved lasting av XMPMetadata ved konvertering av Image til PDF/A, {}",
				ioe.message
			)
		}
		try {
			KonverterTilPdf::class.java.getResourceAsStream("/fonts/icc/AdobeRGB1998.icc").use { colorProfile ->
				// sRGB output intent
				val intent = PDOutputIntent(doc, colorProfile)
				intent.info = "AdobeRGB1998"
				intent.outputCondition = "sRGB IEC61966-2.1"
				intent.outputConditionIdentifier = "sRGB IEC61966-2.1"
				intent.registryName = "http://www.color.org"
				doc.documentCatalog.addOutputIntent(intent)
			}
		} catch (e: IOException) {
			logger.error("Feil ved lasting av XMPMetadata ved konvertering av Image til PDF/A, {}", e.message)
		}
	}

	private fun getScaledDimension(originalWidth: Int, originalHeight: Int): Dimension {
		val newWidth: Float
		val newHeight: Float
		if (originalWidth < originalHeight) {
			newWidth = PDRectangle.A4.width
			newHeight = newWidth * originalHeight / originalWidth
		} else {
			newHeight = PDRectangle.A4.height
			newWidth = newHeight * originalWidth / originalHeight
		}
		return Dimension(newWidth.toInt(), newHeight.toInt())
	}

}
