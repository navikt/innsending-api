package no.nav.soknad.innsending.rest.fyllut

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.utils.Cluster
import no.nav.soknad.innsending.api.FyllutUinnloggetApi
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.EnvQualifier
import no.nav.soknad.innsending.model.KvitteringsDto
import no.nav.soknad.innsending.model.UinnloggetSoknadDto
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.service.filestorage.FileStorageService
import no.nav.soknad.innsending.util.Constants
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
	val soknadService: SoknadService,
	val fileStorageService: FileStorageService,
): FyllutUinnloggetApi {

override fun opprettUinnloggetSoknad(uinnloggetSoknadDto: UinnloggetSoknadDto, envQualifier: EnvQualifier?): ResponseEntity<KvitteringsDto> {

	if (uinnloggetSoknadDto.soknadDto.innsendingsId == null) {
		throw IllegalActionException(
			message = "InnsendingId er ikke satt",
			errorCode = ErrorCode.PROPERTY_NOT_SET,
		)
	}

	verifiserInput(uinnloggetSoknadDto)
	verifiserBrukerId(uinnloggetSoknadDto.soknadDto.brukerId)
	verifiserIdFil(uinnloggetSoknadDto)
	verifiserOpplastedeFiler(uinnloggetSoknadDto)

	lagreUinnloggetSoknad(uinnloggetSoknadDto)

	sendInnUinnloggetSoknad(uinnloggetSoknadDto)

	publiserBrukerNotifikasjonsVarselOmUinnloggetSoknad(
		uinnloggetSoknadDto = uinnloggetSoknadDto
	)


	// Midlertidig løsning for å håndtere uinnlogget søknad
	val kvitteringsDto = KvitteringsDto(
		innsendingsId = uinnloggetSoknadDto.soknadDto.innsendingsId ?: "Ukjent innsendingsId",
		mottattdato = OffsetDateTime.now(),
		label = uinnloggetSoknadDto.soknadDto.tittel ?: "Ukjent tittel",
		hoveddokumentRef = null, // Hoveddokument er ikke relevant for uinnlogget søknad
		innsendteVedlegg = emptyList(),
		skalEttersendes = emptyList(),
		skalSendesAvAndre = emptyList(),
		sendesIkkeInn = emptyList(),
		levertTidligere = emptyList(),
		navKanInnhente = emptyList(),
	)
	return ResponseEntity
		.status(HttpStatus.OK)
		.body(kvitteringsDto)

}

private fun verifiserInput(uinnloggetSoknadDto: UinnloggetSoknadDto) {
	if (uinnloggetSoknadDto.soknadDto.brukerId.isNullOrBlank()) {
		throw IllegalActionException(
			message = "InnsendingId er ikke satt i søknaden",
			errorCode = ErrorCode.PROPERTY_NOT_SET,
		)
	}
	uinnloggetSoknadDto.fileList.forEach {
		fileStorageService.slettFil()
		if (it.fileId.isNullOrBlank()) {
			throw IllegalActionException(
				message = "FilId er ikke satt i filen",
			)
		}
	}

	}

}
