package no.nav.soknad.innsending.service.fillager

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
class FillagerService(
	private val filValidatorService: FilValidatorService,
	cloudStorageConfig: CloudStorageConfig,
	@Qualifier("cloudStorageClient") private val storage: Storage,
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	private val bucket = cloudStorageConfig.fillagerBucketNavn

	fun lagreFil(fil: Resource, vedleggId: String, innsendingId: String, namespace: FillagerNamespace): FilMetadata {
		val filId = UUID.randomUUID().toString()
		val filtype = filValidatorService.validerFil(
			fil = fil,
			innsendingsId = innsendingId,
			antivirusEnabled = true,
		)
		val filinnholdBytes = fil.contentAsByteArray
		val blobNavn = "${namespace.value}/$innsendingId/$vedleggId/$filId"
		// TODO konverter til PDF

		logger.info("$innsendingId: Fil validert ok - $blobNavn (filtype: $filtype)")
		val blobInfo = BlobInfo
			.newBuilder(BlobId.of(bucket, blobNavn))
			.setMetadata(
				mapOf(
					"filId" to filId,
					"vedleggId" to vedleggId,
					"innsendingId" to innsendingId,
					"filtype" to filtype,
					"filnavn" to fil.filename,
				)
			)
			.build()
		storage.writer(blobInfo).use {
			it.write(ByteBuffer.wrap(filinnholdBytes, 0, filinnholdBytes.size))
		}
		logger.info("$innsendingId: Fil lagret til bucket $bucket ($blobNavn)")
		return FilMetadata(
			filId = filId,
			vedleggId = vedleggId,
			innsendingId = innsendingId,
			filnavn = fil.filename ?: "ukjent",
			storrelse = filinnholdBytes.size,
			filtype = filtype,
		)
	}

	fun slettFil(filId: String, innsendingId: String, namespace: FillagerNamespace): Boolean {
		val prefix = "${namespace.value}/$innsendingId/"
		val blobs = storage.list(bucket, Storage.BlobListOption.prefix(prefix))
		val blob = blobs.iterateAll().firstOrNull { it.metadata?.get("filId") == filId }
		return if (blob != null) {
			if (storage.delete(blob.blobId)) {
				logger.info("$innsendingId: Slettet fil med id $filId i bucket $bucket")
				true
			} else {
				logger.warn("$innsendingId: Kunne ikke slette fil med id $filId i bucket $bucket")
				false
			}
		} else {
			logger.warn("$innsendingId: Fant ingen fil med id $filId i bucket $bucket som kunne slettes")
			false
		}
	}

	fun slettFiler(innsendingId: String, vedleggId: String?, namespace: FillagerNamespace): Boolean {
		val prefix = "${namespace.value}/$innsendingId/" + (vedleggId?.let { "$it/" } ?: "")
		val blobs = storage.list(bucket, Storage.BlobListOption.prefix(prefix))
		val successfulDeletions = blobs.iterateAll()
			.map { blob -> storage.delete(blob.blobId) }
			.filter { it }
		logger.info("$innsendingId: Slettet ${successfulDeletions.size} filer i bucket $bucket")
		return successfulDeletions.isNotEmpty()
	}

}
