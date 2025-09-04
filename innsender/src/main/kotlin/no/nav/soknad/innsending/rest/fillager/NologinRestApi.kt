package no.nav.soknad.innsending.rest.fillager

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.NologinApi
import no.nav.soknad.innsending.model.LastOppFilResponse
import no.nav.soknad.innsending.service.config.ConfigDefinition
import no.nav.soknad.innsending.service.config.annotation.VerifyConfigValue
import no.nav.soknad.innsending.service.fillager.FillagerNamespace
import no.nav.soknad.innsending.service.fillager.FillagerService
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.supervision.timer.Timed
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.Utilities
import no.nav.soknad.innsending.util.stringextensions.toUUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@ProtectedWithClaims(
	issuer = Constants.AZURE,
	claimMap = ["roles=nologin-access"],
)
class NologinRestApi(
	private val fillagerService: FillagerService,
) : NologinApi {

	@VerifyConfigValue(
		config = ConfigDefinition.NOLOGIN_MAIN_SWITCH,
		value = "on",
		httpStatus = HttpStatus.SERVICE_UNAVAILABLE,
		message = "NOLOGIN is not available"
	)
	@Timed(InnsenderOperation.LAST_OPP_NOLOGIN)
	override fun lastOppFil(
		vedleggId: String,
		filinnhold: MultipartFile,
		innsendingId: UUID?
	): ResponseEntity<LastOppFilResponse> {
		val innsendingIdString = innsendingId?.toString() ?: Utilities.laginnsendingsId()
		val metadata = fillagerService.lagreFil(
			fil = filinnhold.resource,
			vedleggId = vedleggId,
			innsendingId = innsendingIdString,
			namespace = FillagerNamespace.NOLOGIN,
		)
		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(
				LastOppFilResponse(
					filId = metadata.filId.toUUID(),
					vedleggId = metadata.vedleggId,
					innsendingId = metadata.innsendingId.toUUID(),
					filnavn = metadata.filnavn,
					storrelse = metadata.storrelse,
				)
			)
	}

	@Timed(InnsenderOperation.SLETT_FIL_NOLOGIN)
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

	@Timed(InnsenderOperation.SLETT_FILER_NOLOGIN)
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
