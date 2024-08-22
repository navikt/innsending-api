package no.nav.soknad.innsending.rest.endtoend

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.EndtoendApi
import no.nav.soknad.innsending.model.ArkiveringsStatusDto
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.util.Constants
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("endtoend")
@ProtectedWithClaims(
	issuer = Constants.TOKENX,
	claimMap = [Constants.CLAIM_ACR_LEVEL_4, Constants.CLAIM_ACR_IDPORTEN_LOA_HIGH],
	combineWithOr = true
)
class EndToEndRestApi(
	private val soknadService: SoknadService
) : EndtoendApi {

	override fun getArkiveringsstatus(innsendingsId: String): ResponseEntity<ArkiveringsStatusDto> {
		val soknad = soknadService.hentSoknad(innsendingsId)
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(soknad.arkiveringsStatus)
	}

}
