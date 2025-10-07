package no.nav.soknad.innsending.util.models.skjemadto

import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.SkjemaDtoV2

fun SkjemaDtoV2.getBrukerOrAvsender(): String {
	return this.brukerDto?.id ?: this.avsenderId?.id ?: this.avsenderId?.navn ?: throw IllegalActionException("Hverken bruker eller avsender er satt")}
