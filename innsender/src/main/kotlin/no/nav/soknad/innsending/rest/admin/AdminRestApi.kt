package no.nav.soknad.innsending.rest.admin

import no.nav.soknad.innsending.api.AdminApi
import no.nav.soknad.innsending.cleanup.TempCleanupArchiveFailure
import no.nav.soknad.innsending.model.RunJobRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController

@RestController
@PreAuthorize("@claimChecker.hasAccess(authentication, true, {'scp=admin-access defaultaccess', 'scope=admin-access defaultaccess'}, false)")
class AdminRestApi(
	private val tempCleanupArchiveFailure: TempCleanupArchiveFailure,
) : AdminApi {
	private val logger = LoggerFactory.getLogger(javaClass)

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
