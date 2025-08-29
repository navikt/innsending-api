package no.nav.soknad.innsending.rest.fyllut

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.utils.Cluster
import no.nav.soknad.innsending.api.NologinSoknadApi
import no.nav.soknad.innsending.exceptions.ServiceUnavailableException
import no.nav.soknad.innsending.model.EnvQualifier
import no.nav.soknad.innsending.model.KvitteringsDto
import no.nav.soknad.innsending.model.SkjemaDtoV2
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.service.NologinSoknadService
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.service.config.ConfigDefinition
import no.nav.soknad.innsending.service.config.ConfigService
import no.nav.soknad.innsending.service.config.verifyValue
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.logging.CombinedLogger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
@ProtectedWithClaims(
	issuer = Constants.AZURE,
	claimMap = ["roles=unauthenticated-file-storage-access"],
	excludedClusters = [Cluster.DEV_GCP]
)
class NologinSoknadRestApi(
	private var subjectHandler: SubjectHandlerInterface,
	val soknadService: SoknadService,
	val nologinSoknadService: NologinSoknadService,
	private val configService: ConfigService,
	): NologinSoknadApi {

	private val logger = LoggerFactory.getLogger(javaClass)
	private val secureLogger = LoggerFactory.getLogger("secureLogger")
	private val combinedLogger = CombinedLogger(logger, secureLogger)


	override fun opprettNologinSoknad(nologinSoknadDto: SkjemaDtoV2, envQualifier: EnvQualifier?): ResponseEntity<KvitteringsDto> {
		configService.getConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH)
			.verifyValue("on") { ServiceUnavailableException("NOLOGIN is not available") }

		// Verifiser at det kun er FyllUt som kaller dette API-et
		val applikasjon = subjectHandler.getClientId()
		val brukerId = nologinSoknadService.brukerAvsenderValidering(nologinSoknadDto)
		combinedLogger.log(
			"[${applikasjon}] - Kall for å opprette og sende inn søknad av uinlogget bruker fra applikasjon ${applikasjon} på skjema ${nologinSoknadDto.skjemanr}",
			brukerId
		)

		nologinSoknadService.verifiserInput(nologinSoknadDto)

		val innsendtSoknad = nologinSoknadService.lagreOgSendInnUinnloggetSoknad(nologinSoknadDto, applikasjon)


		// Publiserer bruker-notifikasjon (type beskjed for varsling om innsendt søknad til bruker/avsender) for uinnlogget v kommer senere
	/*
		publiserBrukerNotifikasjonsVarselOmUinnloggetSoknad(
			uinnloggetSoknadDto = uinnloggetSoknadDto
		)
	*/

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(innsendtSoknad)
	}

}
