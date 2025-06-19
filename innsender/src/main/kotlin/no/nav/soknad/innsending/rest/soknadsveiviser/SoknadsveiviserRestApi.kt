package no.nav.soknad.innsending.rest.soknadsveiviser

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.SoknadsveiviserApi
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.EnvQualifier
import no.nav.soknad.innsending.model.OpprettEttersendingGittSkjemaNr
import no.nav.soknad.innsending.model.OpprettSoknadBody
import no.nav.soknad.innsending.util.Constants
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.RestController

// Soknadsveiviser (old dokumentinnsending) creates a link to sendinn-frontend with query parameters to create a new soknad/ettersendelse
// Since soknadsveiviser is deprecated and will be removed, it is separated from the sendinn folder
@RestController
@CrossOrigin(maxAge = 3600)
@ProtectedWithClaims(
	issuer = Constants.TOKENX,
	claimMap = [Constants.CLAIM_ACR_LEVEL_4, Constants.CLAIM_ACR_IDPORTEN_LOA_HIGH],
	combineWithOr = true
)
class SoknadsveiviserRestApi : SoknadsveiviserApi {

	private val logger = LoggerFactory.getLogger(javaClass)

	override fun opprettSoknad(
		opprettSoknadBody: OpprettSoknadBody,
		navEnvQualifier: EnvQualifier?,
	): ResponseEntity<DokumentSoknadDto> {
		val message = "Opprettelse av søknad med visningstype dokumentinnsending er ikke støttet lenger"
		logger.warn("$message (${opprettSoknadBody.skjemanr} ${opprettSoknadBody.vedleggsListe}).")
		throw UnsupportedOperationException(message)
	}

	override fun opprettEttersendingGittSkjemanr(
		opprettEttersendingGittSkjemaNr: OpprettEttersendingGittSkjemaNr,
		navEnvQualifier: EnvQualifier?,
	): ResponseEntity<DokumentSoknadDto> {
		val message = "Opprettelse av ettersending fra søknadsveiviser er ikke støttet lenger"
		logger.warn("$message (${opprettEttersendingGittSkjemaNr.skjemanr} ${opprettEttersendingGittSkjemaNr.vedleggsListe}).")
		throw UnsupportedOperationException(message)
	}

}
