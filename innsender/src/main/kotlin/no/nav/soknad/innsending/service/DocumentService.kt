package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.Mimetype
import no.nav.soknad.innsending.service.fillager.BlobMetadata
import no.nav.soknad.innsending.service.fillager.FilMetadata
import no.nav.soknad.innsending.service.fillager.File
import no.nav.soknad.innsending.service.fillager.FileMetadata
import no.nav.soknad.innsending.service.fillager.FileStorage
import no.nav.soknad.innsending.service.fillager.FileStorageNamespace
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.util.stringextensions.toUUID
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
	private val antivirusScanService: AntivirusScanService,
	private val konverterTilPdf: KonverterTilPdfInterface,
	private val innsenderMetrics: InnsenderMetrics,
	private val pdfMerger: PdfMerger,
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	fun saveAttachment(
		namespace: FileStorageNamespace,
		fil: Resource,
		vedleggId: String,
		innsendingsId: UUID,
		language: String? = "nb",
	): FilMetadata {
		val innsendingsIdString = innsendingsId.toString()
		val validatedFile = filValidatorService.validateFile(fil, innsendingsIdString)
		val fileName = fil.filename ?: "$innsendingsId-$vedleggId${validatedFile.fileType}"
		val effectiveScanMode = if (namespace == FileStorageNamespace.DIGITAL) {
			AntivirusScanMode.ASYNCHRONOUS
		} else {
			AntivirusScanMode.SYNCHRONOUS
		}

		if (effectiveScanMode == AntivirusScanMode.SYNCHRONOUS) {
			antivirusScanService.scanSynchronously(
				namespace = namespace,
				fileContent = validatedFile.content,
				innsendingsId = innsendingsId,
				attachmentId = vedleggId,
			)
		}

		val (fileContentAsPdf, antallSider) = konverterTilPdf.tilPdf(
			validatedFile.content,
			innsendingsIdString,
			validatedFile.fileType,
			fileName,
			language
		)
		filValidatorService.validerAntallSider(antallSider)
		innsenderMetrics.setFileNumberOfPages(antallSider.toLong())

		logger.info("$innsendingsId: Fil validert ok og konvertert til pdf (filtype: ${validatedFile.fileType}, antall sider: $antallSider)")
		val savedFile = fileStorage.save(namespace, fileContentAsPdf, BlobMetadata(
			fileName = fileName,
			attachmentId = vedleggId,
			innsendingsId = innsendingsId,
			fileType = validatedFile.fileType,
			mimetype = Mimetype.applicationSlashPdf,
			language = language
		))

		if (effectiveScanMode == AntivirusScanMode.ASYNCHRONOUS) {
			antivirusScanService.scanAsynchronously(
				namespace = namespace,
				fileContent = validatedFile.content,
				innsendingsId = innsendingsId,
				attachmentId = vedleggId,
				fileId = savedFile.filId,
			)
		}

		return savedFile
	}

	fun saveMainDocument(namespace: FileStorageNamespace, innsendingsId: UUID, fil: Resource, skjemanr: String, mimetype: Mimetype, language: String? = "nb"): FilMetadata {
		val fileType = when (mimetype) {
			Mimetype.applicationSlashPdf -> ".pdf"
			Mimetype.applicationSlashJson -> ".json"
			Mimetype.applicationSlashXml -> ".xml"
			else -> throw IllegalArgumentException("Ugyldig mimetype for hoveddokument: $mimetype")
		}
		val generatedFileName = "hoveddokument-$skjemanr-$innsendingsId$fileType"
		logger.info("$innsendingsId: Lagrer hoveddokument med filnavn=$generatedFileName og mimetype=$mimetype")
		return fileStorage.save(namespace, fil.contentAsByteArray, BlobMetadata(
			fileName = generatedFileName,
			attachmentId = skjemanr,
			innsendingsId = innsendingsId,
			fileType = fileType,
			mimetype = mimetype,
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
		return fileStorage.delete(namespace, innsendingsId, attachmentId, fileId) > 0
	}

	fun mergeFiles(namespace: FileStorageNamespace, innsendingsId: UUID, fileIds: List<UUID>): ByteArray? {
		return fileStorage.getAllFiles(namespace, innsendingsId, fileIds)
			.let { files ->
				if (files.size != fileIds.size) {
					val missingFileIds = fileIds - files.map { it.metadata.filId.toUUID() }.toSet()
					logger.warn("$innsendingsId: Kunne ikke hente filer siden følgende mangler: $missingFileIds")
					throw ResourceNotFoundException("Finner ikke alle filer, ${missingFileIds.size} mangler")
				}
				if (files.isEmpty()) return null
				if (files.size == 1) return files[0].innhold
				if (files.all { it.metadata.mimetype == Mimetype.applicationSlashPdf }) {
					logger.info("$innsendingsId: Skal merge ${files.size} PDF-filer med id-ene ${fileIds.joinToString(", ")}")
					pdfMerger.mergePdfer(files.mapNotNull { it.innhold })
				} else {
					val fileIdsNotPdf = files.filter { it.metadata.mimetype != Mimetype.applicationSlashPdf }.map { it.metadata.filId }
					logger.warn("$innsendingsId: Kunne ikke hente filer siden følgende ikke er pdf: $fileIdsNotPdf")
					throw IllegalStateException("Alle filer må være PDF for å kunne merges")
				}
			}
	}

}
