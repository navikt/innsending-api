package no.nav.soknad.innsending.service.fillager

import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID

@Profile("endtoend")
@Service
class FileStorageMock : FileStorage {
	private val logger = LoggerFactory.getLogger(javaClass)

	private val mockBucket = mutableMapOf<String, Pair<FileMetadata, ByteArray>>()

	override fun save(
		namespace: FileStorageNamespace,
		fileContent: ByteArray,
		blobMetadata: BlobMetadata
	): FileMetadata {
		val innsendingsId = blobMetadata.innsendingsId
		val attachmentId = blobMetadata.attachmentId
		val fileId = UUID.randomUUID().toString()
		val blobName = "${namespace.value}/$innsendingsId/$fileId"
		val fileMetadata = FileMetadata(
			fileId,
			attachmentId,
			innsendingsId.toString(),
			blobMetadata.fileName,
			fileContent.size,
			blobMetadata.fileType,
			FilStatus.LASTET_OPP,
			blobMetadata.mimetype,
			OffsetDateTime.now()
		)
		logger.info("$innsendingsId: Fil lagret til fillager med id $fileId")

		mockBucket[blobName] = Pair(fileMetadata, fileContent)
		return fileMetadata
	}

	override fun getFile(
		namespace: FileStorageNamespace,
		innsendingsId: UUID,
		fileId: UUID
	): File? {
		val blobName = "${namespace.value}/$innsendingsId/${fileId}"
		val blob = mockBucket[blobName]
		return if (blob != null) {
			File(blob.second, blob.first)
		} else {
			logger.warn("$innsendingsId: Finner ikke fil med id $fileId")
			null
		}
	}

	override fun delete(
		namespace: FileStorageNamespace,
		innsendingsId: UUID,
		attachmentId: String?,
		fileId: UUID?,
		permanent: Boolean
	): Boolean {
		val keyPrefix = "${namespace.value}/$innsendingsId"
		val removedKeys = mockBucket.keys.filter { it.startsWith(keyPrefix) }
			.filter { blobName -> attachmentId == null || mockBucket[blobName]?.first?.vedleggId == attachmentId }
			.filter { blobName -> fileId == null || mockBucket[blobName]?.first?.filId == fileId.toString() }
			.also { logger.info("$innsendingsId: Sletter ${it.size} filer for vedlegg $attachmentId") }
			.mapNotNull { mockBucket.remove(it) }
		return removedKeys.isNotEmpty()
	}


	override fun getAllFiles(
		namespace: FileStorageNamespace,
		innsendingsId: UUID,
		fileIds: List<UUID>,
		skipContent: Boolean,
	): List<File> {
		return fileIds.mapNotNull { fileId ->
			val blobName = "${namespace.value}/$innsendingsId/${fileId}"
			mockBucket[blobName]?.let { (metadata, content) ->
				File(if (skipContent) null else content, metadata)
			}
		}
	}
}
