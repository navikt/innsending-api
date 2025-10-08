package no.nav.soknad.innsending.exceptions.utils

val Throwable.messageForLog: String
	get() = this.message ?: this.cause?.message ?: this.suppressed.firstOrNull()?.message ?: this.javaClass.simpleName
