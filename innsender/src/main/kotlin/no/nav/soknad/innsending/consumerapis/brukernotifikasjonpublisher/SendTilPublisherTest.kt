package no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher

import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.arkivering.soknadsmottaker.model.SoknadRef
import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("spring | test | docker | default")
@Qualifier("notifikasjon")
class SendTilPublisherTest: PublisherInterface, HealthRequestInterface {

	override fun ping(): String {
		return "pong"
	}

	override fun isReady(): String {
		return "ok"
	}

	override fun isAlive(): String {
		return "ok"
	}

	override fun avsluttBrukernotifikasjon(soknadRef: SoknadRef) {
	}

	override fun opprettBrukernotifikasjon(nyNotifikasjon: AddNotification) {
	}
}
