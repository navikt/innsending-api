package no.nav.soknad.innsending.brukernotifikasjon.kafka

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerializer
import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.soknad.innsending.config.AppConfiguration
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.header.Headers
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
@Profile("spring | test | docker | default")
class KafkaPublisherTest(appConfiguration: AppConfiguration): KafkaPublisherInterface  {

	private val appConfig = appConfiguration.kafkaConfig


	override fun putApplicationMessageOnTopic(key: Nokkel, value: Beskjed, headers: Headers) {
		val topic = appConfig.kafkaTopicBeskjed
	}

	override fun putApplicationTaskOnTopic(key: Nokkel, value: Oppgave, headers: Headers) {
		val topic = appConfig.kafkaTopicOppgave
	}

	override fun putApplicationDoneOnTopic(key: Nokkel, value: Done, headers: Headers) {
		val topic = appConfig.kafkaTopicDone
	}


}
