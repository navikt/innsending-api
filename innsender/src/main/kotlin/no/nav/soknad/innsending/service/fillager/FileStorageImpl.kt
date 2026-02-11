package no.nav.soknad.innsending.service.fillager

import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import no.nav.soknad.innsending.config.CloudStorageConfig
import no.nav.soknad.innsending.model.Mimetype
import no.nav.soknad.innsending.util.stringextensions.toUUID
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.time.OffsetDateTime
import java.util.*

@Profile("!endtoend")
@Service
class FileStorageImpl(
	cloudStorageConfig: CloudStorageConfig,
	@param:Qualifier("cloudStorageClient") private val storage: Storage,
) : FileStorage {

	private val logger = LoggerFactory.getLogger(javaClass)

	private val bucket = cloudStorageConfig.fillagerBucketNavn

	override fun save(
		namespace: FileStorageNamespace,
		fileContent: ByteArray,
		blobMetadata: BlobMetadata
	): FileMetadata {
		val (fileName, attachmentId, innsendingsId, fileType, mimetype, language) = blobMetadata

		val fileId = UUID.randomUUID().toString()
		val blobName = "${namespace.value}/$innsendingsId/$attachmentId/$fileId"
		val fileStatus = FilStatus.LASTET_OPP
		val fileSize = fileContent.size
		val blobInfo = BlobInfo
			.newBuilder(BlobId.of(bucket, blobName))
			.setMetadata(
				mapOf(
					"filId" to fileId,
					"vedleggId" to attachmentId,
					"innsendingId" to innsendingsId.toString(),
					"filtype" to fileType,
					"filnavn" to fileName,
					"status" to fileStatus.value,
					"storrelse" to fileSize.toString(),
					"mimetype" to mimetype.value,
					"language" to language,
				)
			)
			.build()
		storage.writer(blobInfo).use {
			it.write(ByteBuffer.wrap(fileContent, 0, fileSize))
		}
		logger.info("$innsendingsId: Fil for vedlegg $attachmentId lagret til bucket $bucket ($blobName)")
		return FilMetadata(
			filId = fileId,
			vedleggId = attachmentId,
			innsendingId = innsendingsId.toString(),
			filnavn = blobMetadata.fileName,
			storrelse = fileSize,
			filtype = blobMetadata.fileType,
			status = fileStatus,
			mimetype = blobMetadata.mimetype,
			createdAt = OffsetDateTime.now(),
		)
	}

	override fun getFile(namespace: FileStorageNamespace, innsendingsId: UUID, fileId: UUID): File? {
		val blob = getBlobs(namespace, innsendingsId).getFile(fileId)
		return if (blob != null) File(
			innhold = storage.readAllBytes(blob.blobId),
			metadata = blob.toMetadataDto()
		) else null
	}

	override fun delete(
		namespace: FileStorageNamespace,
		innsendingsId: UUID,
		attachmentId: String?,
		fileId: UUID?,
		permanent: Boolean
	): Int {
		val (deleteCount, failedCount) = getBlobs(namespace, innsendingsId)
			.filter { attachmentId == null || it.metadata?.get("vedleggId") == attachmentId }
			.filter { fileId == null || it.metadata?.get("filId") == fileId.toString() }
			.map { blob -> delete(innsendingsId, blob, permanent) }
			.partition { it }
		if (fileId == null && (deleteCount.isNotEmpty() || failedCount.isNotEmpty())) {
			logger.info("$innsendingsId: Sletting av ${deleteCount.size} filer fullført, ${failedCount.size} feilet (permanent=$permanent)")
		}
		return deleteCount.size
	}

	private fun delete(innsendingsId: UUID, blob: Blob, permanent: Boolean): Boolean {
		if (permanent) {
			return storage.delete(blob.blobId).also { deleted ->
				if (deleted) {
					logger.info("$innsendingsId: Fil ${blob.name} slettet permanent fra bucket $bucket")
				} else {
					logger.warn("$innsendingsId: Kunne ikke slette fil ${blob.name} permanent fra bucket $bucket")
				}
			}
		} else {
			val updatedBlobInfo = blob.toBuilder()
				.setMetadata(blob.metadata?.plus(("status" to FilStatus.SLETTET.value)))
				.build()
			storage.update(updatedBlobInfo)
			logger.info("$innsendingsId: Fil ${blob.name} markert som slettet i bucket $bucket")
			return true
		}
	}

	override fun getAllFiles(
		namespace: FileStorageNamespace,
		innsendingsId: UUID,
		fileIds: List<UUID>,
		skipContent: Boolean,
	): List<File> {
		val blobs = this.getBlobs(namespace, innsendingsId, includeDeleted = true)
			.filter { blob -> fileIds.contains(blob.metadata?.get("filId")?.toUUID()) }
		return blobs.map { File(if (skipContent) null else storage.readAllBytes(it.blobId), it.toMetadataDto()) }
	}

	override fun getFilesCreatedBefore(namespace: FileStorageNamespace, dt: OffsetDateTime): List<FileMetadata> {
		logger.info("Henter filer i namespace ${namespace.value} opprettet tidligere enn $dt")
		return getBlobs(namespace.value)
			.filter { it.createTimeOffsetDateTime.isBefore(dt) }
			.map { it.toMetadataDto() }
	}

	private fun getBlobs(namespace: FileStorageNamespace, innsendingsId: UUID, includeDeleted: Boolean = false): List<Blob> {
		return getBlobs("${namespace.value}/$innsendingsId/")
			.toList()
			.filter { includeDeleted || it.metadata?.get("status") != FilStatus.SLETTET.value }
	}

	private fun getBlobs(prefix: String): List<Blob> {
		val blobs = storage.list(bucket, Storage.BlobListOption.prefix(prefix))
		return blobs.iterateAll().toList()
	}
}

fun List<Blob>.getFile(filId: UUID): Blob? = this.firstOrNull { it.metadata?.get("filId") == filId.toString() }

fun Blob.toMetadataDto(): FilMetadata {
	val filId = this.metadata?.get("filId") ?: "ukjent"
	val vedleggId = this.metadata?.get("vedleggId") ?: "ukjent"
	val innsendingId = this.metadata?.get("innsendingId") ?: "ukjent"
	val filnavn = this.metadata?.get("filnavn") ?: "ukjent"
	val storrelse = this.metadata?.get("storrelse")?.toInt() ?: 0
	val filtype = this.metadata?.get("filtype") ?: "ukjent"
	val mimetype = this.metadata?.get("mimetype")?.let { Mimetype.forValue(it) }
	val status = this.metadata?.get("status")?.let { FilStatus.from(it) } ?: FilStatus.LASTET_OPP
	val createdAt = this.createTimeOffsetDateTime
	return FilMetadata(filId, vedleggId, innsendingId, filnavn, storrelse, filtype, status, mimetype, createdAt)
}
