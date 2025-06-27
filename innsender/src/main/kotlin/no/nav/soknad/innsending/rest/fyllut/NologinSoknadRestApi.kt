package no.nav.soknad.innsending.rest.fyllut

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.utils.Cluster
import no.nav.soknad.innsending.api.NologinSoknadApi
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.EnvQualifier
import no.nav.soknad.innsending.model.KvitteringsDto
import no.nav.soknad.innsending.model.NologinSoknadDto
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.service.InnsendingService
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.service.fillager.FillagerNamespace
import no.nav.soknad.innsending.service.fillager.FillagerService
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
	val fillagerService: FillagerService,
	private val innsendingService: InnsendingService,
	): NologinSoknadApi {

	private val logger = LoggerFactory.getLogger(javaClass)
	private val secureLogger = LoggerFactory.getLogger("secureLogger")
	private val combinedLogger = CombinedLogger(logger, secureLogger)


	override fun opprettNologinSoknad(nologinSoknadDto: NologinSoknadDto, envQualifier: EnvQualifier?): ResponseEntity<KvitteringsDto> {

		// Verifiser at det kun er FyllUt som kaller dette API-et
		val applikasjon = subjectHandler.getClientId()
		val brukerId = brukerAvsenderValidering(nologinSoknadDto)
		val avsenderId = nologinSoknadDto.brukerOgAvsenderDto.avsenderDto
		combinedLogger.log(
			"[${applikasjon}] - Kall for å opprette og sende inn søknad av uinlogget bruker fra applikasjon ${applikasjon} på skjema ${nologinSoknadDto.soknadDto.skjemanr}",
			brukerId ?: avsenderId.id ?: avsenderId.navn ?: "ukjent bruker/avsender"
		)

		verifiserInput(nologinSoknadDto)

		val lagretSoknad = soknadService.lagreUinnloggetSoknad(nologinSoknadDto)

		val sendtInnSoknad = innsendingService.sendInnNoLoginSoknad(lagretSoknad, nologinSoknadDto.brukerOgAvsenderDto.avsenderDto, nologinSoknadDto.brukerOgAvsenderDto.brukerDto)

		// Publiserer bruker-notifikasjon (type beskjed for varsling om innsendt søknad til bruker/avsender) for uinnlogget søknad kommer senere
	/*
		publiserBrukerNotifikasjonsVarselOmUinnloggetSoknad(
			uinnloggetSoknadDto = uinnloggetSoknadDto
		)
	*/

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(sendtInnSoknad)
	}

	private fun brukerAvsenderValidering(nologinSoknadDto: NologinSoknadDto): String? {
		var brukerId: String? = null
		if (nologinSoknadDto.brukerOgAvsenderDto.brukerDto != null) {
			val brukerDto = nologinSoknadDto.brukerOgAvsenderDto.brukerDto
			if (nologinSoknadDto.soknadDto.brukerId != brukerDto!!.id) {
				throw IllegalActionException("BrukerId i søknaden må være lik brukerId i brukerDto", errorCode = ErrorCode.PROPERTY_NOT_SET) // TODO fiks feilmelding
			}
			brukerId = brukerDto.id
		}

		if (nologinSoknadDto.brukerOgAvsenderDto.avsenderDto.id == null && nologinSoknadDto.brukerOgAvsenderDto.avsenderDto.navn == null) {
			throw IllegalActionException(
				message = "Mangler avsender informasjon, verken id eller navn satt",
				errorCode = ErrorCode.PROPERTY_NOT_SET,
			)
		}
		if (nologinSoknadDto.brukerOgAvsenderDto.avsenderDto.id != null && nologinSoknadDto.brukerOgAvsenderDto.avsenderDto.idType == null) {
			throw IllegalActionException(
				message = "Avsender idType må være satt når avsender id er satt",
				errorCode = ErrorCode.PROPERTY_NOT_SET,
			)
		}
		return brukerId
	}

private fun verifiserInput(uinnloggetSoknadDto: NologinSoknadDto) {
		// InnsendingsId skal være satt av FyllUt
		if (uinnloggetSoknadDto.soknadDto.innsendingsId == null) {
			throw IllegalActionException(
				message = "InnsendingId er ikke satt",
				errorCode = ErrorCode.PROPERTY_NOT_SET,
			)
		}
	// Avsender skal identifiseres enten med: fnr, d-nr, eller navn i arkivet
		if (uinnloggetSoknadDto.soknadDto.brukerId.isNullOrBlank()) {
			throw IllegalActionException(
				message = "InnsendingId er ikke satt i søknaden",
				errorCode = ErrorCode.PROPERTY_NOT_SET,
			)
		}
		if (uinnloggetSoknadDto.soknadDto.visningsType != VisningsType.nologin) {
			throw IllegalActionException(
				message = "Vinsingstype må være nologin",
				errorCode = ErrorCode.PROPERTY_NOT_SET,
			)
		}
		// FyllUt leverer liste av alle filer som skal sendes inn. Vi må verifisere at disse er opplastet slik at soknadsarkivet kan hente dem
		uinnloggetSoknadDto.fileList.forEach {
			if (fillagerService.hentFil(filId = it.fileId, uinnloggetSoknadDto.soknadDto.innsendingsId!!, namespace= FillagerNamespace.NOLOGIN) == null) {
				throw IllegalActionException(
					message = "Fil med id ${it.fileId} finnes ikke for søknaden",
					errorCode = ErrorCode.PROPERTY_NOT_SET,
				)
			}
		}

	}

}
