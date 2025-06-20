package no.nav.soknad.innsending.rest.filestorage

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.utils.Cluster
import no.nav.soknad.innsending.api.UnauthenticatedFileStorageApi
import no.nav.soknad.innsending.model.FileUploadResponse
import no.nav.soknad.innsending.service.filestorage.FileStorageService
import no.nav.soknad.innsending.service.filestorage.StorageNamespace
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.supervision.timer.Timed
import no.nav.soknad.innsending.util.Constants
import org.springframework.core.io.Resource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@ProtectedWithClaims(
	issuer = Constants.AZURE,
	claimMap = ["roles=unauthenticated-file-storage-access"],
	excludedClusters = [Cluster.DEV_GCP]
)
class UnauthenticatedFileStorageRestApi(
	val fileStorageService: FileStorageService,
) : UnauthenticatedFileStorageApi {

	@Timed(InnsenderOperation.LAST_OPP_BUCKET)
	override fun uploadFile(fileContent: Resource, groupId: String?): ResponseEntity<FileUploadResponse> {
		val group = groupId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
		val filId = fileStorageService.lagreFil(
			fil = fileContent,
			groupId = group,
			namespace = StorageNamespace.UNAUTHENTICATED,
		)
		return ResponseEntity.ok(FileUploadResponse(id = filId, groupId = group))
	}

	@Timed(InnsenderOperation.SLETT_FIL_BUCKET)
	override fun deleteFile(fileId: String, groupId: String): ResponseEntity<Unit> {
		fileStorageService.slettFil(
			fileId = fileId,
			groupId = groupId,
			namespace = StorageNamespace.UNAUTHENTICATED,
		)
		return ResponseEntity.noContent().build()
	}

	@Timed(InnsenderOperation.SLETT_FILER_BUCKET)
	override fun deleteFiles(groupId: String): ResponseEntity<Unit> {
		fileStorageService.slettFiler(
			groupId = groupId,
			namespace = StorageNamespace.UNAUTHENTICATED,
		)
		return ResponseEntity.noContent().build()
	}

}
