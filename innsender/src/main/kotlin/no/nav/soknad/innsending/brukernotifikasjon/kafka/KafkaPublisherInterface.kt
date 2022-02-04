package no.nav.soknad.innsending.brukernotifikasjon.kafka

import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.header.internals.RecordHeaders

interface KafkaPublisherInterface {
	fun putApplicationMessageOnTopic(key: Nokkel, value: Beskjed, headers: Headers = RecordHeaders())

	fun putApplicationTaskOnTopic(key: Nokkel, value: Oppgave, headers: Headers = RecordHeaders())

	fun putApplicationDoneOnTopic(key: Nokkel, value: Done, headers: Headers = RecordHeaders())
}
