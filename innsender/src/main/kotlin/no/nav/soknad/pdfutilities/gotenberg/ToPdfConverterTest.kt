package no.nav.soknad.pdfutilities.gotenberg

import no.nav.soknad.pdfutilities.DocxToPdfInterface
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.common.PDStream
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component


@Component
@Profile("!(prod | dev | test)")
class ToPdfConverterTest : DocxToPdfInterface {

	override fun toPdf(fileName: String, fileContent: ByteArray): ByteArray {
		var document: PDDocument? = null
		try {
			document = PDDocument()
			val pdStream = PDStream(document)
			return pdStream.toByteArray()
		} finally {
			if (document != null) document.close()
		}
	}

}