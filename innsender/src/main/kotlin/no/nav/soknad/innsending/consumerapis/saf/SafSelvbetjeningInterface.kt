package no.nav.soknad.innsending.consumerapis.saf

import no.nav.soknad.innsending.consumerapis.saf.dto.ArkiverteSaker

interface SafSelvbetjeningInterface {

	fun hentBrukersSakerIArkivet(brukerId: String): List<ArkiverteSaker>
}
