package no.nav.soknad.innsending.location

import no.nav.soknad.innsending.model.EnvQualifier

interface UrlHandler {
	fun getSendInnUrl(envQualifier: EnvQualifier? = null): String
	fun getFyllutUrl(envQualifier: EnvQualifier? = null): String
}
