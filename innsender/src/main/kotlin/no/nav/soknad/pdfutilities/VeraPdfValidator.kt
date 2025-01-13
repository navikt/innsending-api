package no.nav.soknad.pdfutilities

import org.verapdf.pdfa.Foundries
import java.io.ByteArrayInputStream
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider
import org.verapdf.pdfa.flavours.PDFAFlavour

class VeraPDFValidator {

	fun validatePdf(pdfFile: ByteArray): ValidationCheckResult {

		try {
			VeraGreenfieldFoundryProvider.initialise()

			val validator = Foundries.defaultInstance().createValidator(PDFAFlavour.PDFA_2_B, false)
			val parser = Foundries.defaultInstance().createParser(ByteArrayInputStream(pdfFile), PDFAFlavour.PDFA_1_B, PDFAFlavour.PDFA_2_B)
			val validationResult = validator.validate(parser)
			return if (validationResult.isCompliant)
				ValidationCheckResult(isPdfACompliant = true, pdfAFlavour = validationResult.pdfaFlavour.name, hasPdfUaSupport = false)
			else
				ValidationCheckResult(isPdfACompliant = false, pdfAFlavour = validationResult.pdfaFlavour.name, hasPdfUaSupport = false)
		} catch (exception: Exception) {
			throw exception
		}

	}
}

data class ValidationCheckResult(
	val isPdfACompliant: Boolean,
	val pdfAFlavour: String,
	val hasPdfUaSupport: Boolean
)

