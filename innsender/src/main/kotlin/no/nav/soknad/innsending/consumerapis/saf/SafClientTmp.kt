package no.nav.soknad.innsending.consumerapis.saf

import no.nav.soknad.innsending.consumerapis.saf.dto.ArkiverteSaker
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("!(test | dev | prod)")
class SafClientTmp : SafClientInterface {
	override fun hentDokumentoversiktBruker(brukerId: String): List<ArkiverteSaker> = emptyList()
}
