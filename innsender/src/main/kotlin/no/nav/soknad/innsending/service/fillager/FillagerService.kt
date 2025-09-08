package no.nav.soknad.innsending.service.fillager

import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import no.nav.soknad.innsending.config.CloudStorageConfig
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.service.FilValidatorService
import no.nav.soknad.pdfutilities.KonverterTilPdfInterface
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.util.UUID

@Profile("!endtoend")
@Service
class FillagerService(
	private val filValidatorService: FilValidatorService,
	private val konverterTilPdf: KonverterTilPdfInterface,
	cloudStorageConfig: CloudStorageConfig,
	@Qualifier("cloudStorageClient") private val storage: Storage,
)  :FillagerInterface {
	private val logger = LoggerFactory.getLogger(javaClass)

	private val bucket = cloudStorageConfig.fillagerBucketNavn

	// TODO Legg til språk i nologin api
	override fun lagreFil(fil: Resource, vedleggId: String, innsendingId: String, namespace: FillagerNamespace, spraak: String?): FilMetadata {
		val filtype = filValidatorService.validerFil(
			fil = fil,
			innsendingsId = innsendingId,
			antivirusEnabled = true,
		)
		val filinnholdBytes = fil.contentAsByteArray
		val filId = UUID.randomUUID().toString()
		val blobNavn = "${namespace.value}/$innsendingId/$vedleggId/$filId"

		val (filSomPdf, antallSider) = konverterTilPdf.tilPdf(
			filinnholdBytes,
			innsendingId,
			filtype,
			fil.filename ?: "ukjent",
			spraak
		)

		logger.info("$innsendingId: Fil validert ok og konvertert til pdf - $bucket,  $blobNavn (filtype: $filtype, antall sider: $antallSider)")
		filValidatorService.validerAntallSider(antallSider)

		val fileStatus = FilStatus.LASTET_OPP
		val blobInfo = BlobInfo
			.newBuilder(BlobId.of(bucket, blobNavn))
			.setMetadata(
				mapOf(
					"filId" to filId,
					"vedleggId" to vedleggId,
					"innsendingId" to innsendingId,
					"filtype" to filtype,
					"filnavn" to fil.filename,
					"status" to fileStatus.value,
					"antallSider" to antallSider.toString(),
				)
			)
			.build()
		storage.writer(blobInfo).use {
			it.write(ByteBuffer.wrap(filSomPdf, 0, filSomPdf.size))
		}
		logger.info("$innsendingId: Fil lagret til bucket $bucket ($blobNavn)")
		return FilMetadata(
			filId = filId,
			vedleggId = vedleggId,
			innsendingId = innsendingId,
			filnavn = fil.filename ?: "ukjent",
			storrelse = filSomPdf.size,
			filtype = filtype,
			status = fileStatus,
		)
	}

	override fun hentFil(filId: String, innsendingId: String, namespace: FillagerNamespace): Fil? {
		val blob = hentFilBlob(namespace, innsendingId, filId)
		return if (blob != null) {
			val innhold = storage.readAllBytes(blob.blobId)
			val storrelse = innhold.size
			val filnavn = blob.metadata?.get("filnavn") ?: "ukjent"
			val vedleggId = blob.metadata?.get("vedleggId") ?: "ukjent"
			val filtype = blob.metadata?.get("filtype") ?: "ukjent"
			val status = blob.metadata?.get("status")?.let { FilStatus.from(it) } ?: FilStatus.LASTET_OPP
			val metadata = FilMetadata(filId, vedleggId, innsendingId, filnavn, storrelse, filtype, status)
			Fil(innhold, metadata)
		} else {
			logger.warn("$innsendingId: Fant ingen fil med id $filId i bucket $bucket")
			null
		}
	}

	override fun hentFilinnhold(filId: String, innsendingId: String, namespace: FillagerNamespace): ByteArray? {
		return hentFil(filId, innsendingId, namespace)?.innhold
	}

	override fun oppdaterStatusForInnsending(innsendingId: String, namespace: FillagerNamespace, status: FilStatus) {
		val prefix = "${namespace.value}/$innsendingId/"
		val blobs = storage.list(bucket, Storage.BlobListOption.prefix(prefix))
		blobs.iterateAll().forEach { blob ->
			val updatedMetadata = (blob.metadata ?: emptyMap()) + ("status" to status.value)
			val updatedBlobInfo = blob.toBuilder().setMetadata(updatedMetadata).build()
			storage.update(updatedBlobInfo)
		}
	}

	override fun slettFil(filId: String, innsendingId: String, namespace: FillagerNamespace): Boolean {
		val blob = hentFilBlob(namespace, innsendingId, filId)
		return if (blob != null) {
			if (blob.metadata?.get("status") == FilStatus.INNSENDT.value) {
				throw IllegalActionException("Kan ikke slette fil med status INNSENDT")
			}
			if (storage.delete(blob.blobId, Storage.BlobSourceOption.metagenerationMatch(blob.metageneration))) {
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

	override fun slettFiler(innsendingId: String, vedleggId: String?, namespace: FillagerNamespace): Boolean {
		val prefix = "${namespace.value}/$innsendingId/" + (vedleggId?.let { "$it/" } ?: "")
		val blobs = storage.list(bucket, Storage.BlobListOption.prefix(prefix))
		val blobList = blobs.iterateAll().toList()
		if (blobList.any { it.metadata?.get("status") == FilStatus.INNSENDT.value }) {
			throw IllegalActionException("Kan ikke slette fil med status INNSENDT")
		}
		val successfulDeletions = blobList
			.map { blob -> storage.delete(blob.blobId, Storage.BlobSourceOption.metagenerationMatch(blob.metageneration)) }
			.filter { it }
		logger.info("$innsendingId: Slettet ${successfulDeletions.size} filer i bucket $bucket")
		return successfulDeletions.isNotEmpty()
	}

	private fun hentFilBlob(
		namespace: FillagerNamespace,
		innsendingId: String,
		filId: String
	): Blob? {
		val prefix = "${namespace.value}/$innsendingId/"
		val blobs = storage.list(bucket, Storage.BlobListOption.prefix(prefix))
		return blobs.iterateAll().firstOrNull { it.metadata?.get("filId") == filId }
	}

}
