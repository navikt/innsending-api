package no.nav.soknad.innsending.util.models.skjemadto

import no.nav.soknad.innsending.model.SkjemaDtoV2

fun SkjemaDtoV2.getBrukerOrAvsenderForSecureLog(): String {
	return this.brukerDto?.id ?: this.avsenderId?.id ?: this.avsenderId?.navn ?: "-"
}
