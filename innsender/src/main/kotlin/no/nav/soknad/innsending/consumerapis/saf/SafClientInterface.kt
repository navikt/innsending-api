package no.nav.soknad.innsending.consumerapis.saf

import no.nav.soknad.innsending.consumerapis.saf.dto.ArkiverteSaker

interface SafClientInterface {
	fun hentDokumentoversiktBruker(brukerId: String): List<ArkiverteSaker>
}
