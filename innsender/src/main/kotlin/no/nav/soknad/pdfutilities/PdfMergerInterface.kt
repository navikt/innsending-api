package no.nav.soknad.pdfutilities

import org.springframework.web.multipart.MultipartFile

interface PdfMergerInterface {
	fun mergePdfer(docs: List<ByteArray>): ByteArray
}
