package no.nav.soknad.innsending.service.filestorage

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import no.nav.soknad.innsending.config.CloudStorageConfig
import no.nav.soknad.innsending.service.FilValidatorService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.util.UUID

@Service
class FileStorageService(
	private val filValidatorService: FilValidatorService,
	private val cloudStorageConfig: CloudStorageConfig,
	@Qualifier("cloudStorageClient") private val storage: Storage,
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	private val bucketName = cloudStorageConfig.fileStorageBucketName

	fun lagreFil(fil: Resource, groupId: String, namespace: StorageNamespace): String {
		val filId = UUID.randomUUID().toString()
		val filtype = filValidatorService.validerFil(
			fil = fil,
			innsendingsId = groupId,
			antivirusEnabled = true,
		)
		val content = fil.contentAsByteArray
		val blobName = "${namespace.value}/$groupId/$filId"
		// TODO konverter til PDF
		logger.info("$groupId: Fil validert ok - $blobName (filtype: $filtype)")
		val blobInfo = BlobInfo
			.newBuilder(BlobId.of(bucketName, blobName))
			.setMetadata(
				mapOf(
					"filId" to filId,
					"groupId" to groupId,
					"filtype" to filtype,
				)
			)
			.build()
		storage.writer(blobInfo).use {
			it.write(ByteBuffer.wrap(content, 0, content.size))
		}
		logger.info("$groupId: Fil lagret til bucket $bucketName ($blobName)")
		return filId
	}

	fun slettFil(fileId: String, groupId: String, namespace: StorageNamespace) {
		val blobId = BlobId.of(bucketName, "${namespace.value}/$groupId/$fileId")
		if (storage.delete(blobId)) {
			logger.info("$groupId: Slettet fil med id $fileId i bucket $bucketName")
		} else {
			logger.warn("$groupId: Fant ingen fil med id $fileId i bucket $bucketName som kunne slettes")
		}
	}

	fun slettFiler(groupId: String, namespace: StorageNamespace) {
		val prefix = "${namespace.value}/$groupId/"
		val blobs = storage.list(bucketName, Storage.BlobListOption.prefix(prefix))
		val successfulDeletions = blobs.iterateAll().map { blob -> storage.delete(blob.blobId) }.filter { it }
		logger.info("$groupId: Slettet ${successfulDeletions.size} filer i bucket $bucketName")
	}


	fun filEksisterer(fileId: String, vedleggsRef: String, innsendingsId: String, namespace: StorageNamespace): Boolean {
		val blobId = BlobId.of(bucketName, "${namespace.value}/$innsendingsId/$vedleggsRef/$fileId")
		val blob = storage.get(blobId)
		return blob != null && blob.getContent() != null
	}

	fun hentFil(fileId: String, vedleggsRef: String, innsendingsId: String, namespace: StorageNamespace): ByteArray? {
		val blobId = BlobId.of(bucketName, "${namespace.value}/$innsendingsId/$vedleggsRef/$fileId")
		val blob = storage.get(blobId)
		if (blob != null && blob.getContent() != null) {
			logger.info("$innsendingsId: Hentet fil med id $fileId i bucket $bucketName")
			return blob.getContent()
		} else {
			logger.warn("$innsendingsId: Fant ingen fil med id $fileId i bucket $bucketName")
			throw IllegalArgumentException("Fant ingen fil med id $fileId i bucket $bucketName")
		}
	}

}
