package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.model.Mimetype
import no.nav.soknad.innsending.service.fillager.BlobMetadata
import no.nav.soknad.innsending.service.fillager.FilMetadata
import no.nav.soknad.innsending.service.fillager.File
import no.nav.soknad.innsending.service.fillager.FileMetadata
import no.nav.soknad.innsending.service.fillager.FileStorage
import no.nav.soknad.innsending.service.fillager.FileStorageNamespace
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.pdfutilities.KonverterTilPdfInterface
import no.nav.soknad.pdfutilities.PdfMerger
import org.slf4j.LoggerFactory
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class DocumentService(
	private val fileStorage: FileStorage,
	private val filValidatorService: FilValidatorService,
	private val konverterTilPdf: KonverterTilPdfInterface,
	private val innsenderMetrics: InnsenderMetrics,
	private val pdfMerger: PdfMerger,
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	fun saveAttachment(namespace: FileStorageNamespace, fil: Resource, vedleggId: String, innsendingsId: UUID, language: String? = "nb"): FilMetadata {
		val innsendingsIdString = innsendingsId.toString()
		val filtype = filValidatorService.validerFil(
			fil = fil,
			innsendingsId = innsendingsIdString,
			antivirusEnabled = true,
		)
		val fileName = fil.filename ?: "$innsendingsId-$vedleggId$filtype"
		val filinnholdBytes = fil.contentAsByteArray
		val (filinnhold, antallSider) = konverterTilPdf.tilPdf(
			filinnholdBytes,
			innsendingsIdString,
			filtype,
			fileName,
			language
		)
		filValidatorService.validerAntallSider(antallSider)
		innsenderMetrics.setFileNumberOfPages(antallSider.toLong())

		logger.info("$innsendingsId: Fil validert ok og konvertert til pdf (filtype: $filtype, antall sider: $antallSider)")
		return fileStorage.save(namespace, filinnhold, BlobMetadata(
			fileName = fileName,
			attachmentId = vedleggId,
			innsendingsId = innsendingsId,
			fileType = filtype,
			mimetype = Mimetype.applicationSlashPdf,
			language = language
		))
	}

	fun saveMainDocument(namespace: FileStorageNamespace, innsendingsId: UUID, fil: Resource, skjemanr: String, mimetype: Mimetype, language: String? = "nb"): FilMetadata {
		val fileType = when (mimetype) {
			Mimetype.applicationSlashPdf -> ".pdf"
			Mimetype.applicationSlashJson -> ".json"
			Mimetype.applicationSlashXml -> ".xml"
			else -> throw IllegalArgumentException("Ugyldig mimetype for hoveddokument: $mimetype")
		}
		val fileName = "hoveddokument-$skjemanr-$innsendingsId$fileType"
		logger.info("$innsendingsId: Lagrer hoveddokument med filnavn=$fileName og mimetype=$mimetype")
		return fileStorage.save(namespace, fil.contentAsByteArray, BlobMetadata(
			fileName = fileName,
			attachmentId = skjemanr,
			innsendingsId = innsendingsId,
			fileType = fileType,
			mimetype = Mimetype.applicationSlashPdf,
			language = language
		))
	}

	fun getFile(namespace: FileStorageNamespace, innsendingsId: UUID, fileId: UUID): File? {
		return fileStorage.getFile(namespace, innsendingsId, fileId)
	}

	fun getFileMetadata(namespace: FileStorageNamespace, innsendingsId: UUID, fileIds: List<UUID>): List<FileMetadata> {
		return fileStorage.getAllFiles(namespace, innsendingsId, fileIds, skipContent = true).map { it.metadata }
	}

	fun deleteAttachment(namespace: FileStorageNamespace, innsendingsId: UUID, attachmentId: String? = null, fileId: UUID? = null): Boolean {
		return fileStorage.delete(namespace, innsendingsId, attachmentId, fileId)
	}

	fun mergeFiles(namespace: FileStorageNamespace, innsendingsId: UUID, fileIds: List<UUID>): ByteArray? {
		return fileStorage.getAllFiles(namespace, innsendingsId, fileIds)
			.let { files ->
				if (files.isEmpty()) return null
				if (files.size == 1) return files[0].innhold
				if (files.all { it.metadata.mimetype == Mimetype.applicationSlashPdf }) {
					logger.info("$innsendingsId: Skal merge ${files.size} PDF-filer med id-ene ${fileIds.joinToString(", ")}")
					pdfMerger.mergePdfer(files.mapNotNull { it.innhold })
				} else throw IllegalStateException(
					"$innsendingsId: Kunne ikke merge siden ikke alle er pdf ${fileIds.joinToString(", ")}"
				)
			}
	}

}
