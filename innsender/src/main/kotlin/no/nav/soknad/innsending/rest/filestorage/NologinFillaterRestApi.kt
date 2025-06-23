package no.nav.soknad.innsending.rest.filestorage

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.utils.Cluster
import no.nav.soknad.innsending.api.NologinFillagerApi
import no.nav.soknad.innsending.model.LastOppFilResponse
import no.nav.soknad.innsending.service.filestorage.FillagerService
import no.nav.soknad.innsending.service.filestorage.FillagerNamespace
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.supervision.timer.Timed
import no.nav.soknad.innsending.util.Constants
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@ProtectedWithClaims(
	issuer = Constants.AZURE,
	claimMap = ["roles=unauthenticated-file-storage-access"],
	excludedClusters = [Cluster.DEV_GCP]
)
class NologinFillaterRestApi(
	val fillagerService: FillagerService,
) : NologinFillagerApi {

	@Timed(InnsenderOperation.LAST_OPP_BUCKET)
	override fun lastOppFil(
		vedleggId: String,
		filinnhold: Resource,
		innsendingId: UUID?
	): ResponseEntity<LastOppFilResponse> {
		val innsendingIdString = innsendingId?.toString() ?: UUID.randomUUID().toString()
		val filId = fillagerService.lagreFil(
			fil = filinnhold,
			vedleggId = vedleggId,
			innsendingId = innsendingIdString,
			namespace = FillagerNamespace.NOLOGIN,
		)
		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(
				LastOppFilResponse(
					filId = UUID.fromString(filId),
					vedleggId = vedleggId,
					innsendingId = UUID.fromString(innsendingIdString)
				)
			)
	}

	@Timed(InnsenderOperation.SLETT_FIL_BUCKET)
	override fun slettFilV2(filId: UUID, innsendingId: UUID): ResponseEntity<Unit> {
		val deleted = fillagerService.slettFil(
			filId = filId.toString(),
			innsendingId = innsendingId.toString(),
			namespace = FillagerNamespace.NOLOGIN,
		)
		return if (deleted) {
			ResponseEntity.noContent().build()
		} else {
			ResponseEntity.notFound().build()
		}
	}

	@Timed(InnsenderOperation.SLETT_FILER_BUCKET)
	override fun slettFiler(innsendingId: UUID, vedleggId: String?): ResponseEntity<Unit> {
		val deleted = fillagerService.slettFiler(
			innsendingId = innsendingId.toString(),
			vedleggId = vedleggId,
			namespace = FillagerNamespace.NOLOGIN,
		)
		return if (deleted) {
			ResponseEntity.noContent().build()
		} else {
			ResponseEntity.notFound().build()
		}
	}

}
