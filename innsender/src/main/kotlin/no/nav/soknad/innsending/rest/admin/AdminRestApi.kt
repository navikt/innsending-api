package no.nav.soknad.innsending.rest.admin

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.soknad.innsending.api.AdminApi
import no.nav.soknad.innsending.cleanup.TempCleanupArchiveFailure
import no.nav.soknad.innsending.exceptions.ForbiddenException
import no.nav.soknad.innsending.model.AdminArkiveringsstatus
import no.nav.soknad.innsending.model.OppdaterArkiveringsstatusRequest
import no.nav.soknad.innsending.model.OppdaterArkiveringsstatusResponse
import no.nav.soknad.innsending.model.RunJobRequest
import no.nav.soknad.innsending.service.admin.AdminArkiveringsstatusService
import no.nav.soknad.innsending.util.Constants
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
@ProtectedWithClaims(issuer = Constants.AZURE)
class AdminRestApi(
	private val tempCleanupArchiveFailure: TempCleanupArchiveFailure,
	private val adminArkiveringsstatusService: AdminArkiveringsstatusService,
	private val tokenValidationContextHolder: TokenValidationContextHolder,
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
		innsendingsId: String,
		oppdaterArkiveringsstatusRequest: OppdaterArkiveringsstatusRequest,
	): ResponseEntity<OppdaterArkiveringsstatusResponse> {
		val claims = tokenValidationContextHolder.getTokenValidationContext().getClaims(Constants.AZURE)
		adminArkiveringsstatusService.verifiserTilgang(claims.getStringClaim(CLAIM_AZP_NAME))

		val navIdent = claims.getStringClaim(CLAIM_NAV_IDENT)?.takeIf { it.isNotBlank() }
			?: claims.getStringClaim(CLAIM_PREFERRED_USERNAME)?.takeIf { it.isNotBlank() }
			?: throw ForbiddenException("Fant ikke brukeridentitet i token")

		if (oppdaterArkiveringsstatusRequest.arkiveringsstatus != AdminArkiveringsstatus.Arkivert) {
			throw IllegalArgumentException("Kan kun sette arkiveringsstatus til ${AdminArkiveringsstatus.Arkivert}")
		}

		adminArkiveringsstatusService.settArkivert(
			innsendingsId = innsendingsId,
			begrunnelse = oppdaterArkiveringsstatusRequest.begrunnelse,
			navIdent = navIdent,
		)

		return ResponseEntity.ok(
			OppdaterArkiveringsstatusResponse(
				innsendingsId = innsendingsId,
				arkiveringsstatus = AdminArkiveringsstatus.Arkivert,
			)
		)
	}

	private companion object {
		const val CLEANUP_KLAR_FOR_INNSENDING = "cleanup-klar-for-innsending"
		const val CLAIM_AZP_NAME = "azp_name"
		const val CLAIM_NAV_IDENT = "NAVident"
		const val CLAIM_PREFERRED_USERNAME = "preferred_username"
	}
}
