package no.nav.soknad.innsending.rest.fyllut

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.NologinSoknadApi
import no.nav.soknad.innsending.model.EnvQualifier
import no.nav.soknad.innsending.model.KvitteringsDto
import no.nav.soknad.innsending.model.SkjemaDtoV2
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.service.NologinSoknadService
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.service.config.ConfigDefinition
import no.nav.soknad.innsending.service.config.annotation.VerifyConfigValue
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.supervision.timer.Timed
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.logging.CombinedLogger
import no.nav.soknad.innsending.util.models.skjemadto.getBrukerOrAvsenderForSecureLog
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
@ProtectedWithClaims(
	issuer = Constants.AZURE,
	claimMap = ["roles=nologin-access"],
)
class NologinSoknadRestApi(
	private var subjectHandler: SubjectHandlerInterface,
	val soknadService: SoknadService,
	val nologinSoknadService: NologinSoknadService,
	): NologinSoknadApi {

	private val logger = LoggerFactory.getLogger(javaClass)
	private val combinedLogger = CombinedLogger(logger)

	@VerifyConfigValue(
		config = ConfigDefinition.NOLOGIN_MAIN_SWITCH,
		value = "on",
		httpStatus = HttpStatus.SERVICE_UNAVAILABLE,
		message = "NOLOGIN is not available"
	)
	@Timed(InnsenderOperation.SEND_INN_NOLOGIN)
	override fun opprettNologinSoknad(nologinSoknadDto: SkjemaDtoV2, envQualifier: EnvQualifier?): ResponseEntity<KvitteringsDto> {
		val applikasjon = subjectHandler.getClientId()
		val brukerAvsender = nologinSoknadDto.getBrukerOrAvsenderForSecureLog()
		combinedLogger.log(
			"${nologinSoknadDto.innsendingsId}: Kall for å opprette og sende inn ${nologinSoknadDto.skjemanr} av uinnlogget bruker (applikasjon $applikasjon)",
			brukerAvsender
		)

		nologinSoknadService.verifiserInput(nologinSoknadDto)

		val innsendtSoknad = nologinSoknadService.lagreOgSendInnUinnloggetSoknad(nologinSoknadDto, applikasjon)

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(innsendtSoknad)
	}

}
