package no.nav.soknad.innsending.brukernotifikasjon.kafka

import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.soknad.innsending.config.KafkaConfig
import org.apache.kafka.common.header.Headers
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("spring | test | docker | default")
class KafkaPublisherTest(private val kafkaConfig: KafkaConfig): KafkaPublisherInterface  {

	override fun putApplicationMessageOnTopic(key: Nokkel, value: Beskjed, headers: Headers) {
		val topic = kafkaConfig.kafkaTopicBeskjed
	}

	override fun putApplicationTaskOnTopic(key: Nokkel, value: Oppgave, headers: Headers) {
		val topic = kafkaConfig.kafkaTopicOppgave
	}

	override fun putApplicationDoneOnTopic(key: Nokkel, value: Done, headers: Headers) {
		val topic = kafkaConfig.kafkaTopicDone
	}


}
