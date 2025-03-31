package no.nav.soknad.innsending.util.soknaddbdata

import no.nav.soknad.innsending.repository.domain.models.SoknadDbData
import no.nav.soknad.innsending.util.Skjema.createSkjemaPathFromSkjemanr

fun SoknadDbData.isEttersending(): Boolean = ettersendingsid != null

fun SoknadDbData.getSkjemaPath(): String = createSkjemaPathFromSkjemanr(skjemanr)
