package no.nav.soknad.innsending.rest.fyllut

import jakarta.validation.Valid
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.FyllutMergeApi
import no.nav.soknad.innsending.model.MergeFilerDto
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.pdfutilities.PdfMergerInterface
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
@ProtectedWithClaims(issuer = Constants.AZURE)
class FyllutMerge(private val pdfMerger: PdfMergerInterface): FyllutMergeApi {
	private val logger = LoggerFactory.getLogger(javaClass)

	override fun fyllUtMergeFiler( @Valid @RequestBody mergeFilerDto: MergeFilerDto): ResponseEntity<ByteArray> {

		logger.info("FyllutMerge: Merge fil til ${mergeFilerDto.tittel}, Språk = ${mergeFilerDto.sprak?: "nb-NO"}, Antall filer = ${mergeFilerDto.filer.size}")

		val mergedFile = pdfMerger.mergeWithPDFBox(mergeFilerDto.filer)
		val mergedFileMedMetadata = pdfMerger.setPdfMetadata(mergedFile, mergeFilerDto.tittel, mergeFilerDto.sprak)
		logger.info("FyllutMerge: Returnerer fil ${mergedFileMedMetadata.size}")

		return ResponseEntity.status(HttpStatus.OK).body(mergedFileMedMetadata)
	}

}
