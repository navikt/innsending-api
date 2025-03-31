package no.nav.soknad.testutils

import no.nav.soknad.pdfutilities.FileToPdfInterface
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.common.PDStream

class ToPdfConverterTest : FileToPdfInterface {

	override fun toPdf(fileName: String, fileContent: ByteArray): ByteArray {
		var document: PDDocument? = null
		try {
			document = PDDocument()
			val pdStream = PDStream(document)
			return pdStream.toByteArray()
		} finally {
			document?.close()
		}
	}

	override fun imageToPdf(fileName: String, fileContent: ByteArray): ByteArray {
		return toPdf(fileName, fileContent)
	}

	override fun mergePdfs(fileName: String, metadata: String, docs: List<ByteArray>): ByteArray {
		return toPdf(fileName, docs.first())
	}

	override fun flattenPdfs(fileName: String, metadata: String, docs: List<ByteArray>): ByteArray {
		return toPdf(fileName,  docs.first())
	}

	override fun buildMetadata(title: String?, subject: String?, author: String?, keywords: List<String>?): String {
		return "{\"title\":\"$title\", \"subject\":\"$subject\", \"author\":\"$author\"}"
	}

}
