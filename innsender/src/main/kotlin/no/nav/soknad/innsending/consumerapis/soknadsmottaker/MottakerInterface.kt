package no.nav.soknad.innsending.consumerapis.soknadsmottaker

import no.nav.soknad.innsending.model.AvsenderDto
import no.nav.soknad.innsending.model.BrukerDto
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.VedleggDto

interface MottakerInterface {

	fun sendInnSoknad(soknadDto: DokumentSoknadDto, vedleggsListe: List<VedleggDto>, avsenderDto: AvsenderDto, brukerDto: BrukerDto? = null)
}
