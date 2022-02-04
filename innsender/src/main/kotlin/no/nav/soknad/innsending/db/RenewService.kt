package no.nav.soknad.innsending.db

import no.nav.soknad.innsending.ApplicationState

interface RenewService {
	fun startRenewTasks(applicationState: ApplicationState)
}
