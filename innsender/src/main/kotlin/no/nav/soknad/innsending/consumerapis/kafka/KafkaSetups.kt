package no.nav.soknad.innsending.consumerapis.kafka

import no.nav.soknad.innsending.config.KafkaConfig
import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.*
import org.apache.kafka.common.config.SslConfigs.*
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate


@Configuration
@Profile("prod | dev | endtoend | loadtests")
class KafkaPublishConfiguration(
	private val kafkaConfig: KafkaConfig,
) {
	private val stringKeySerializerClass = StringSerializer::class.java
	private val stringValueSerializerClass = StringSerializer::class.java

	@Bean
	fun kvitteringsSideFactory() = DefaultKafkaProducerFactory<String, String>(createKafkaConfig(stringKeySerializerClass, stringValueSerializerClass))

	@Bean
	fun kvitteringsSideTemplate() = KafkaTemplate(kvitteringsSideFactory())

	fun <T : Serializer<*>> createKafkaConfig(keySerializer: Class<T>, valueSerializer: Class<T>? = null): HashMap<String, Any> {

		return HashMap<String, Any>().also {
			it[BOOTSTRAP_SERVERS_CONFIG] = kafkaConfig.brokers
			it[KEY_SERIALIZER_CLASS_CONFIG] = keySerializer
			it[VALUE_SERIALIZER_CLASS_CONFIG] = valueSerializer ?: stringValueSerializerClass
			it[MAX_BLOCK_MS_CONFIG] = 30000
			it[ACKS_CONFIG] = "all"
			it[ENABLE_IDEMPOTENCE_CONFIG] = "false"
			if (kafkaConfig.security.enabled == "TRUE") {
				it[SECURITY_PROTOCOL_CONFIG] = kafkaConfig.security.protocol!!
				it[SSL_KEYSTORE_TYPE_CONFIG] = "PKCS12"
				it[SSL_TRUSTSTORE_LOCATION_CONFIG] = kafkaConfig.security.trustStorePath!!
				it[SSL_TRUSTSTORE_PASSWORD_CONFIG] = kafkaConfig.security.trustStorePassword!!
				it[SSL_KEYSTORE_LOCATION_CONFIG] = kafkaConfig.security.keyStorePath!!
				it[SSL_KEYSTORE_PASSWORD_CONFIG] = kafkaConfig.security.keyStorePassword!!
				it[SSL_KEY_PASSWORD_CONFIG] = kafkaConfig.security.keyStorePassword
			}
		}
	}

}
