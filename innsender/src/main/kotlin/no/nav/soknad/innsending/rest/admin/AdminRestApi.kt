package no.nav.soknad.innsending.rest.admin

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.AdminApi
import no.nav.soknad.innsending.cleanup.TempCleanupArchiveFailure
import no.nav.soknad.innsending.model.RunJobRequest
import no.nav.soknad.innsending.util.Constants
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
@ProtectedWithClaims(issuer = Constants.AZURE)
class AdminRestApi(
	private val tempCleanupArchiveFailure: TempCleanupArchiveFailure,
) : AdminApi {
	private val logger = LoggerFactory.getLogger(javaClass)

	@ProtectedWithClaims(
		issuer = Constants.AZURE,
		claimMap = ["scp=job-admin-access defaultaccess"],
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

	private companion object {
		const val CLEANUP_KLAR_FOR_INNSENDING = "cleanup-klar-for-innsending"
	}
}
