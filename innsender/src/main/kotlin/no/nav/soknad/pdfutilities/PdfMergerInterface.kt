package no.nav.soknad.pdfutilities

import org.springframework.web.multipart.MultipartFile

interface PdfMergerInterface {
	fun mergePdfer(title: String? = null, subject: String? = null, docs: List<ByteArray>): ByteArray
}
