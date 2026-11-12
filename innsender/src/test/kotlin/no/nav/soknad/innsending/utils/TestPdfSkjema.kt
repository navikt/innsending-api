package no.nav.soknad.innsending.utils

import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.Loader
import java.io.ByteArrayOutputStream

// Lager syntetiske PDF-er for tester av utflating. Verdiene er oppdiktede.
object TestPdfSkjema {

	const val FELTVERDI = "Ærlig Østby-Åsen"

	enum class Skjema { INGEN_ACROFORM, TOM_ACROFORM, UTFYLT_TEKSTFELT, WIDGET_UTEN_UTSEENDE, WIDGET_MED_UTSEENDE }

	fun lag(skjema: Skjema, antallSider: Int = 1, verdi: String = FELTVERDI): ByteArray =
		PDDocument().use { document ->
			repeat(antallSider) { document.addPage(PDPage(PDRectangle.A4)) }

			// Widgets uten AcroForm. Uten utseende: slik qpdf etterlater et tomt felt. Med utseende: noe som fortsatt vises.
			if (skjema == Skjema.WIDGET_UTEN_UTSEENDE || skjema == Skjema.WIDGET_MED_UTSEENDE) {
				val widget = PDAnnotationWidget()
				widget.rectangle = PDRectangle(50f, 600f, 400f, 24f)
				if (skjema == Skjema.WIDGET_MED_UTSEENDE) {
					val utseende = PDAppearanceStream(document)
					utseende.bBox = PDRectangle(400f, 24f)
					widget.appearance = PDAppearanceDictionary().apply { setNormalAppearance(utseende) }
				}
				document.getPage(0).annotations.add(widget)
			}

			if (skjema == Skjema.TOM_ACROFORM || skjema == Skjema.UTFYLT_TEKSTFELT) {
				val acroForm = PDAcroForm(document)
				document.documentCatalog.acroForm = acroForm

				if (skjema == Skjema.UTFYLT_TEKSTFELT) {
					val resources = PDResources()
					resources.put(COSName.getPDFName("Helv"), PDType1Font(Standard14Fonts.FontName.HELVETICA))
					acroForm.defaultResources = resources
					acroForm.defaultAppearance = "/Helv 12 Tf 0 g"

					val felt = PDTextField(acroForm)
					felt.partialName = "navn"
					felt.defaultAppearance = "/Helv 12 Tf 0 g"
					acroForm.fields.add(felt)

					val side = document.getPage(0)
					val widget = felt.widgets.first()
					widget.rectangle = PDRectangle(50f, 700f, 400f, 24f)
					widget.page = side
					widget.isPrinted = true
					side.annotations.add(widget)

					felt.value = verdi
				}
			}

			ByteArrayOutputStream().use { out ->
				document.save(out)
				out.toByteArray()
			}
		}

	fun tekst(pdf: ByteArray): String =
		Loader.loadPDF(pdf).use { PDFTextStripper().getText(it) }

	fun harSkjemafeltEllerWidgets(pdf: ByteArray): Boolean =
		Loader.loadPDF(pdf).use { document ->
			document.documentCatalog.acroForm?.fields?.isNotEmpty() == true ||
				document.pages.any { side -> side.annotations.any { it.subtype == "Widget" } }
		}

	fun antallSider(pdf: ByteArray): Int = Loader.loadPDF(pdf).use { it.numberOfPages }
}
