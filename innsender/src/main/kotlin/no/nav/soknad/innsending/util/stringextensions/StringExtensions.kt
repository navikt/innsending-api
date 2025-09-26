package no.nav.soknad.innsending.util.stringextensions

import java.util.UUID

fun String.toUUID(): UUID = UUID.fromString(this)
