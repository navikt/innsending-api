package no.nav.soknad.innsending.consumerapis.saf

import no.nav.soknad.innsending.consumerapis.saf.dto.ArkiverteSaker

interface SafInterface {

	fun hentBrukersSakerIArkivet(brukerId: String): List<ArkiverteSaker>?
}
