package no.nav.soknad.innsending.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "kafka")
data class KafkaConfig(
	val applicationId: String,
	val brokers: String,
	val security: SecurityConfig,
	val topics: Topics,
)

data class SecurityConfig(
	val enabled: String,
	val protocol: String,
	val keyStoreType: String,
	val keyStorePath: String,
	val keyStorePassword: String,
	val trustStorePath: String,
	val trustStorePassword: String
)

data class Topics(
	val messageTopic: String,
)
