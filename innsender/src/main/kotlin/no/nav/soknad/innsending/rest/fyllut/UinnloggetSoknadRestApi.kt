package no.nav.soknad.innsending.rest.fyllut

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.utils.Cluster
import no.nav.soknad.innsending.api.FyllutUinnloggetApi
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.EnvQualifier
import no.nav.soknad.innsending.model.KvitteringsDto
import no.nav.soknad.innsending.model.UinnloggetSoknadDto
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.InnsendingService
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.service.filestorage.FileStorageService
import no.nav.soknad.innsending.service.filestorage.StorageNamespace
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.logging.CombinedLogger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@RestController
@ProtectedWithClaims(
	issuer = Constants.AZURE,
	claimMap = ["roles=unauthenticated-file-storage-access"],
	excludedClusters = [Cluster.DEV_GCP]
)
class UinnloggetSoknadRestApi(
	private var subjectHandler: SubjectHandlerInterface,
	val soknadService: SoknadService,
	val fileStorageService: FileStorageService,
	private val innsendingService: InnsendingService,
	): FyllutUinnloggetApi {

	private val logger = LoggerFactory.getLogger(javaClass)
	private val secureLogger = LoggerFactory.getLogger("secureLogger")
	private val combinedLogger = CombinedLogger(logger, secureLogger)


	override fun opprettUinnloggetSoknad(uinnloggetSoknadDto: UinnloggetSoknadDto, envQualifier: EnvQualifier?): ResponseEntity<KvitteringsDto> {

		// Verifiser at det kun er FyllUt som kaller dette API-et
		val applikasjon = subjectHandler.getClientId()
		val brukerId = uinnloggetSoknadDto.soknadDto.brukerId ?: "Navn må settes for å arkivere søknaden"
		combinedLogger.log(
			"[${applikasjon}] - Kall for å opprette og sende inn søknad av uinlogget bruker fra applikasjon ${applikasjon} på skjema ${uinnloggetSoknadDto.soknadDto.skjemanr}",
			brukerId
		)

		verifiserInput(uinnloggetSoknadDto)

		val lagretSoknad = soknadService.lagreUinnloggetSoknad(uinnloggetSoknadDto)

		val sendtInnSoknad = innsendingService.sendInnUinLoggetSoknad(lagretSoknad)

		// Publiserer bruker-notifikasjon for uinnlogget søknad kommer senere
	/*
		publiserBrukerNotifikasjonsVarselOmUinnloggetSoknad(
			uinnloggetSoknadDto = uinnloggetSoknadDto
		)
	*/

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(sendtInnSoknad)
	}

private fun verifiserInput(uinnloggetSoknadDto: UinnloggetSoknadDto) {
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
			if (!fileStorageService.filEksisterer(it.fileId, vedleggsRef = it.vedleggRef, uinnloggetSoknadDto.soknadDto.innsendingsId!!, StorageNamespace.UNAUTHENTICATED) != null) {
				throw IllegalActionException(
					message = "Fil med id ${it.fileId} finnes ikke for søknaden",
					errorCode = ErrorCode.PROPERTY_NOT_SET,
				)
			}
		}

	}

}
