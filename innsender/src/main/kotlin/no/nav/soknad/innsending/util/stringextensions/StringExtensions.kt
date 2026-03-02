package no.nav.soknad.innsending.util.stringextensions

import no.nav.soknad.innsending.rest.validering.removeInvalidControlCharacters
import java.util.UUID

fun String.toUUID(): UUID = UUID.fromString(this)

fun String.removeInvalidControlCharacters(): String = removeInvalidControlCharacters(this)
