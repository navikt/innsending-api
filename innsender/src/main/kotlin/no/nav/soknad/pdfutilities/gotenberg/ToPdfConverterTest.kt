package no.nav.soknad.pdfutilities.gotenberg

import no.nav.soknad.pdfutilities.FileToPdfInterface
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDSimpleFont
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.io.IOException


@Component
@Profile("!(prod | dev | test)")
class ToPdfConverterTest : FileToPdfInterface {

	override fun toPdf(fileName: String, fileContent: ByteArray): ByteArray {
		return dummyPdf()
	}

	override fun imageToPdf(fileName: String, fileContent: ByteArray): ByteArray {
		return toPdf(fileName, fileContent)
	}

	override fun mergePdfs(fileName: String, docs: List<ByteArray>): ByteArray {
		return toPdf(fileName, docs.first())
	}

	private fun dummyPdf(): ByteArray {
		val document = PDDocument()

		try {
			val page = PDPage()
			document.addPage(page)
			PDPageContentStream(document, page).use { contentStream ->
				contentStream.beginText()
				contentStream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12f)
				contentStream.newLineAtOffset(100f, 700f)
				contentStream.showText("Hello World")
				contentStream.endText()
			}

			val outputStream = ByteArrayOutputStream()
			document.save(outputStream)

			return outputStream.toByteArray()
		} finally {
			document.close()
		}
	}
}
