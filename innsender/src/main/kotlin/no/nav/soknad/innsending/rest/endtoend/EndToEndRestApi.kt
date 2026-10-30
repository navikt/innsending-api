package no.nav.soknad.innsending.rest.endtoend

import no.nav.security.token.support.core.api.Unprotected
import no.nav.soknad.innsending.api.EndtoendApi
import no.nav.soknad.innsending.model.ArkiveringsStatusDto
import no.nav.soknad.innsending.security.KreverInnbygger
import no.nav.soknad.innsending.service.SoknadService
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
@KreverInnbygger
@Profile("endtoend")
// TokenX-token valideres av Texas og tilgangen av @PreAuthorize (#461, steg 3j)
@Unprotected
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
