package no.nav.soknad.innsending.rest.ettersending


import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.FrontendApi
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.SafService
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.logging.CombinedLogger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.RestController

@RestController
@CrossOrigin(maxAge = 3600)
@ProtectedWithClaims(issuer = Constants.TOKENX, claimMap = [Constants.CLAIM_ACR_IDPORTEN_LOA_HIGH])
class EttersendingRestApi(
	val soknadService: SoknadService,
	val tilgangskontroll: Tilgangskontroll,
	private val safService: SafService,
) : FrontendApi {

	private val logger = LoggerFactory.getLogger(javaClass)
	private val secureLogger = LoggerFactory.getLogger("secureLogger")
	private val combinedLogger = CombinedLogger(logger, secureLogger)

	// TODO: Implement endpoint for ettersending

}

