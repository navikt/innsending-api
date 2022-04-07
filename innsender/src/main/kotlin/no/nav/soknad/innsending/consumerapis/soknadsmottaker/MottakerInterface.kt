package no.nav.soknad.innsending.consumerapis.soknadsmottaker

import no.nav.soknad.innsending.model.DokumentSoknadDto

interface MottakerInterface {

	fun sendInnSoknad(soknadDto: DokumentSoknadDto)
}
