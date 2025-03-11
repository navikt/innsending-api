package no.nav.soknad.innsending.util.dokumentsoknad

import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.VisningsType

fun DokumentSoknadDto.isLospost() = this.visningsType == VisningsType.lospost
