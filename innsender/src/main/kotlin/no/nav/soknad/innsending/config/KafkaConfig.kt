package no.nav.soknad.innsending.config

import no.nav.soknad.innsending.ProfileConfig
import org.springframework.boot.context.properties.ConfigurationProperties
import kotlin.properties.Delegates

@ConfigurationProperties(prefix = "kafkaconfig")
class KafkaConfig(private val profileConfig: ProfileConfig) {
	lateinit var profiles: String
	lateinit var tjenesteUrl: String
	lateinit var gjenopptaSoknadsArbeid: String
	lateinit var ettersendePaSoknad: String
	lateinit var username: String
	lateinit var password: String
	lateinit var servers: String
	lateinit var schemaRegistryUrl: String
	lateinit var clientId: String
	lateinit var secure: String
	lateinit var protocol: String // SASL_PLAINTEXT | SASL_SSL
	lateinit var salsmec: String // PLAIN
	lateinit var kafkaTopicBeskjed: String
	lateinit var kafkaTopicOppgave: String
	lateinit var kafkaTopicDone: String
	var publisereEndringer by Delegates.notNull<Boolean>()

	fun setProfiles() {profiles = profileConfig.profil}
	fun getSaslJaasConfig(): String = "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$username\" password=\"$password\";"

}
