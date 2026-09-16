package no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher

import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.arkivering.soknadsmottaker.model.SoknadRef

interface PublisherInterface {
	fun avsluttBrukernotifikasjon(soknadRef: SoknadRef)

	fun opprettBrukernotifikasjon(nyNotifikasjon: AddNotification)

}
