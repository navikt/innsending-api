package no.nav.soknad.innsending.service.fillager

import no.nav.soknad.innsending.model.Mimetype
import java.util.UUID

interface FileStorage {

	fun save(namespace: FileStorageNamespace, fileContent: ByteArray, blobMetadata: BlobMetadata): FileMetadata

	fun getFile(namespace: FileStorageNamespace, innsendingsId: UUID, fileId: UUID): File?

	fun delete(namespace: FileStorageNamespace, innsendingsId: UUID, attachmentId: String? = null, fileId: UUID? = null, permanent: Boolean = false): Boolean

	fun getAllFiles(namespace: FileStorageNamespace, innsendingsId: UUID, fileIds: List<UUID>, skipContent: Boolean = false): List<File>

}

data class BlobMetadata(
	val fileName: String,
	val attachmentId: String,
	val innsendingsId: UUID,
	val fileType: String,
	val mimetype: Mimetype,
	val language: String? = "nb",
)

