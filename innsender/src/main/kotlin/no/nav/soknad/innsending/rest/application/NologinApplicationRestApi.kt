package no.nav.soknad.innsending.rest.application

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.NologinApplicationApi
import no.nav.soknad.innsending.model.ApplicationSubmissionResponse
import no.nav.soknad.innsending.model.FileDto
import no.nav.soknad.innsending.model.SubmitApplicationRequest
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.service.DocumentService
import no.nav.soknad.innsending.service.InnsendingService
import no.nav.soknad.innsending.service.NologinSoknadService
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.service.config.ConfigDefinition
import no.nav.soknad.innsending.service.config.annotation.VerifyConfigValue
import no.nav.soknad.innsending.service.fillager.FileStorageNamespace
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.supervision.timer.Timed
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.mapping.filmetadata.toDto
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.*

@RestController
@ProtectedWithClaims(
	issuer = Constants.AZURE,
	claimMap = ["roles=nologin-access"],
)
class NologinApplicationRestApi(
	private val documentService: DocumentService,
	private val innsendingService: InnsendingService,
	private val subjectHandler: SubjectHandlerInterface,
	private val nologinSoknadService: NologinSoknadService,
) : NologinApplicationApi {

	private val logger = LoggerFactory.getLogger(javaClass)

	@VerifyConfigValue(
		config = ConfigDefinition.NOLOGIN_MAIN_SWITCH,
		value = "on",
		httpStatus = HttpStatus.SERVICE_UNAVAILABLE,
		errorCode = no.nav.soknad.innsending.exceptions.ErrorCode.TEMPORARILY_UNAVAILABLE,
		message = "NOLOGIN is not available"
	)
	@Timed(InnsenderOperation.LAST_OPP_NOLOGIN)
	override fun uploadNologinAttachmentFile(
		innsendingsId: UUID,
		attachmentId: String,
		file: MultipartFile
	): ResponseEntity<FileDto> {
		val metadata = documentService.saveAttachment(
			namespace = FileStorageNamespace.NOLOGIN,
			fil = file.resource,
			vedleggId = attachmentId,
			innsendingsId = innsendingsId,
		)
		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(metadata.toDto())
	}

	@Timed(InnsenderOperation.SLETT_FIL_NOLOGIN)
	override fun deleteNologinAttachmentFile(
		innsendingsId: UUID,
		attachmentId: String,
		fileId: UUID
	): ResponseEntity<Unit> {
		documentService.deleteAttachment(
			namespace = FileStorageNamespace.NOLOGIN,
			innsendingsId = innsendingsId,
			attachmentId = attachmentId,
			fileId = fileId,
		)
		return ResponseEntity.noContent().build()
	}

	@Timed(InnsenderOperation.SLETT_FILER_NOLOGIN)
	override fun deleteNologinAttachment(innsendingsId: UUID, attachmentId: String): ResponseEntity<Unit> {
		documentService.deleteAttachment(
			namespace = FileStorageNamespace.NOLOGIN,
			innsendingsId = innsendingsId,
			attachmentId = attachmentId,
		)
		return ResponseEntity.noContent().build()
	}

	@VerifyConfigValue(
		config = ConfigDefinition.NOLOGIN_MAIN_SWITCH,
		value = "on",
		httpStatus = HttpStatus.SERVICE_UNAVAILABLE,
		errorCode = no.nav.soknad.innsending.exceptions.ErrorCode.TEMPORARILY_UNAVAILABLE,
		message = "NOLOGIN is not available"
	)
	@Timed(InnsenderOperation.SEND_INN_NOLOGIN)
	override fun submitNologinApplication(
		innsendingsId: UUID,
		submitApplicationRequest: SubmitApplicationRequest
	): ResponseEntity<ApplicationSubmissionResponse> {
		val clientId = subjectHandler.getClientId()
		logger.info("$innsendingsId: Kall for å sende inn søknad av uinnlogget bruker (applikasjon $clientId)")

		val result = nologinSoknadService.lagreOgSendInnUinnloggetSoknad(innsendingsId, submitApplicationRequest, clientId)

		return ResponseEntity.ok(result)
	}

}
