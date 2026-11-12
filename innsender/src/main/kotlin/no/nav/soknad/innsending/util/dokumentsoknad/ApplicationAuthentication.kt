package no.nav.soknad.innsending.util.dokumentsoknad

import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.safselvbetjening.generated.enums.Kanal

// Owner is not an authentication marker: historical authenticated rows can have SQL NULL owner before backfill.
fun VisningsType?.isUnauthenticatedSubmission() = this == VisningsType.nologin

// The archive channel remains available after the local application has been deleted.
fun Kanal?.isUnauthenticatedSubmission() = this == Kanal.NAV_NO_UINNLOGGET
