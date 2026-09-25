package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.ApplicationSubmissionResponse
import no.nav.soknad.innsending.model.SubmitApplicationRequest
import no.nav.soknad.innsending.util.Constants.TRANSACTION_TIMEOUT
import no.nav.soknad.innsending.util.mapping.createMainDocument
import no.nav.soknad.innsending.util.mapping.toDokumentSoknadDto
import no.nav.soknad.innsending.util.models.attachmentdto.sanitize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class NologinSoknadService(
	private val innsendingService: InnsendingService,
	private val repo: RepositoryUtils,
	private val soknadService: SoknadService,
) {
	@Transactional(timeout = TRANSACTION_TIMEOUT)
	fun lagreOgForberedInnsendingAvUinnloggetSoknad(
		innsendingsId: UUID,
		submitApplicationRequest: SubmitApplicationRequest,
		applikasjon: String
	): ApplicationSubmissionResponse {
		if (repo.existsByInnsendingsId(innsendingsId.toString())) {
			throw IllegalActionException(
				message = "Søknad med innsendingsId $innsendingsId finnes allerede",
				errorCode = ErrorCode.SOKNAD_ALREADY_EXISTS
			)
		}

		val dbSoknad = repo.lagreSoknad(submitApplicationRequest.toDokumentSoknadDto(innsendingsId, applikasjon))
		repo.lagreVedlegg(dbSoknad.createMainDocument())
		repo.lagreVedlegg(dbSoknad.createMainDocument(true))

		val result = innsendingService.preSubmitApplication(
			soknadService.hentSoknad(dbSoknad.id!!),
			submitApplicationRequest.mainDocument,
			submitApplicationRequest.mainDocumentAlt,
			submitApplicationRequest.attachments.sanitize(),
			submitApplicationRequest.avsender,
		)

		return result.first
	}
}
