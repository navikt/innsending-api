package no.nav.soknad.innsending.rest.admin

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.AdminApi
import no.nav.soknad.innsending.cleanup.TempCleanupArchiveFailure
import no.nav.soknad.innsending.exceptions.ForbiddenException
import no.nav.soknad.innsending.model.AdminArkiveringsstatus
import no.nav.soknad.innsending.model.OppdaterArkiveringsstatusRequest
import no.nav.soknad.innsending.model.OppdaterArkiveringsstatusResponse
import no.nav.soknad.innsending.model.RunJobRequest
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.service.admin.AdminArkiveringsstatusService
import no.nav.soknad.innsending.util.Constants
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@ProtectedWithClaims(issuer = Constants.AZURE)
class AdminRestApi(
	private val tempCleanupArchiveFailure: TempCleanupArchiveFailure,
	private val adminArkiveringsstatusService: AdminArkiveringsstatusService,
	private val subjectHandler: SubjectHandlerInterface,
) : AdminApi {
	private val logger = LoggerFactory.getLogger(javaClass)

	@ProtectedWithClaims(
		issuer = Constants.AZURE,
		claimMap = ["scp=admin-access defaultaccess"],
	)
	override fun runJob(runJobRequest: RunJobRequest): ResponseEntity<Unit> {
		logger.info("Invoked admin runJob for jobName=${runJobRequest.jobName}")
		when (runJobRequest.jobName) {
			CLEANUP_KLAR_FOR_INNSENDING -> {
				logger.info("Running cleanup job: $CLEANUP_KLAR_FOR_INNSENDING")
				tempCleanupArchiveFailure.fixAttachmentStatusAndResubmit()
				logger.info("Completed cleanup job: $CLEANUP_KLAR_FOR_INNSENDING")
			}
			else -> throw IllegalArgumentException("Unknown jobName: ${runJobRequest.jobName}")
		}

		return ResponseEntity.status(HttpStatus.CREATED).build()
	}

	@ProtectedWithClaims(
		issuer = Constants.AZURE,
		claimMap = ["scp=admin-access defaultaccess"],
	)
	override fun oppdaterArkiveringsstatus(
		innsendingsId: UUID,
		oppdaterArkiveringsstatusRequest: OppdaterArkiveringsstatusRequest,
	): ResponseEntity<OppdaterArkiveringsstatusResponse> {
		adminArkiveringsstatusService.verifiserTilgang(subjectHandler.getAzureClientName())

		val navIdent = subjectHandler.getAzureUserIdent()
			?: throw ForbiddenException("Fant ikke brukeridentitet i token")

		if (oppdaterArkiveringsstatusRequest.arkiveringsstatus != AdminArkiveringsstatus.Arkivert) {
			throw IllegalArgumentException("Kan kun sette arkiveringsstatus til ${AdminArkiveringsstatus.Arkivert}")
		}

		adminArkiveringsstatusService.settArkivert(
			innsendingsId = innsendingsId.toString(),
			begrunnelse = oppdaterArkiveringsstatusRequest.begrunnelse,
			navIdent = navIdent,
		)

		return ResponseEntity.ok(
			OppdaterArkiveringsstatusResponse(
				innsendingsId = innsendingsId.toString(),
				arkiveringsstatus = AdminArkiveringsstatus.Arkivert,
			)
		)
	}

	private companion object {
		const val CLEANUP_KLAR_FOR_INNSENDING = "cleanup-klar-for-innsending"
	}
}
