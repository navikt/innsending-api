package no.nav.soknad.innsending.config

import com.natpryce.konfig.*
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import no.nav.soknad.innsending.ApplicationState
import no.nav.soknad.innsending.db.*
import org.springframework.context.annotation.Bean
import java.io.File

private val defaultProperties = ConfigurationMap(
	mapOf(
		"APP_VERSION" to "",
		"APPLICATION_USERNAME" to "soknadinnsender",
		"APPLICATION_PASSWORD" to "",
		"APPLICATION_PROFILE" to "spring",
		"SHARED_PASSWORD" to "password",
		"DATABASE_HOST" to "localhost",
		"DATABASE_PORT" to "5432",
		"DATABASE_NAME" to "postgres",
		"DATABASE_JDBC_URL" to "",
		"VAULT_DB_PATH" to "",
		"HENT_FRA_HENVENDELSE" to "false",
		"MAX_FILE_SIZE" to (1024 * 1024 * 100).toString(),
		"SCHEMA_REGISTRY_URL" to "http://localhost:8081",
		"KAFKA_BOOTSTRAP_SERVERS" to "localhost:29092",
		"KAFKA_CLIENTID" to "kafkaproducer",
		"KAFKA_SECURITY" to "",
		"KAFKA_SECPROT" to "",
		"KAFKA_SASLMEC" to "",
		"KAFKA_TOPIC_BESKJED" to "aapen-brukernotifikasjon-nyBeskjed-v1",
		"KAFKA_TOPIC_OPPGAVE" to "aapen-brukernotifikasjon-nyOppgave-v1",
		"KAFKA_TOPIC_DONE" to "aapen-brukernotifikasjon-done-v1",
		"PUBLISERE_BRUKERNOTIFIKASJONER" to "TRUE",
		"TJENESTE_URL" to "https://localhost",
		"GJENOPPTA_SOKNADS_ARBEID" to "/dokumentinnsending/oversikt/",
		"ETTERSENDE_PA_SOKNAD" to "/dokumentinnsending/ettersending/",
		"SANITY_URL" to "http://localhost:8080",
		"FILESTORAGE_HOST" to "http://localhost:9042",
		"FILESTORAGE_ENDPOINT" to "/filer",
		"FILESTORAGE_PARAMETERS" to "?ids=",
		"SOKNADSMOTTAKER_HOST" to "http://localhost:9043",
		"SOKNADSMOTTAKER_ENDPOINT" to "/save"
		)
)

val appConfig =
	EnvironmentVariables() overriding
		systemProperties() overriding
		ConfigurationProperties.fromResource(Configuration::class.java, "/application.yml") overriding
		defaultProperties

private fun String.configProperty(): String = appConfig[Key(this, stringType)]

fun readFileAsText(fileName: String, default: String) = try { File(fileName).readText(Charsets.UTF_8) } catch (e: Exception ) { default }

data class AppConfiguration(val restConfig: RestConfig = RestConfig(), val dbConfig: DBConfig = DBConfig(), val kafkaConfig: KafkaConfig = KafkaConfig()) {
	val applicationState = ApplicationState()

	data class RestConfig(
		val version: String = "APP_VERSION".configProperty(),
		val username: String = readFileAsText("/var/run/secrets/nais.io/serviceuser/username", "APPLICATION_USERNAME".configProperty()),
		val password: String = readFileAsText("/var/run/secrets/nais.io/serviceuser/password", "APPLICATION_PASSWORD".configProperty()),
		val maxFileSize: Int = "MAX_FILE_SIZE".configProperty().toInt(),
		val sanityUrl: String = "SANITY_URL".configProperty(),
		val filestorageHost: String = "FILESTORAGE_HOST".configProperty(),
		val filestorageEndpoint: String = "FILESTORAGE_ENDPOINT".configProperty(),
		val filestorageParameters: String = "FILESTORAGE_PARAMETERS".configProperty(),
		val soknadsMottakerHost: String = "SOKNADSMOTTAKER_HOST".configProperty(),
		val soknadsMottakerEndpoint: String = "SOKNADSMOTTAKER_ENDPOINT".configProperty(),
	)

	data class DBConfig(
		val profiles: String = "APPLICATION_PROFILE".configProperty(),
		val databaseName: String = "DATABASE_NAME".configProperty(),
		val mountPathVault: String = "VAULT_DB_PATH".configProperty(),
		val url: String = "DATABASE_JDBC_URL".configProperty().ifBlank { null } ?: String.format(
			"jdbc:postgresql://%s:%s/%s",
			requireNotNull("DATABASE_HOST".configProperty()) { "database host must be set if jdbc url is not provided" },
			requireNotNull("DATABASE_PORT".configProperty()) { "database port must be set if jdbc url is not provided" },
			requireNotNull("DATABASE_NAME".configProperty()) { "database name must be set if jdbc url is not provided" }),
		val embedded: Boolean = "spring" == profiles,
		val useVault: Boolean = profiles == "dev" || profiles == "prod",
		val credentialService: CredentialService = if (useVault) VaultCredentialService() else EmbeddedCredentialService(),
		val renewService: RenewService = if (useVault) RenewVaultService(credentialService) else EmbeddedRenewService(credentialService)
	)

	data class KafkaConfig(
		val profiles: String = "APPLICATION_PROFILE".configProperty(),
		val tjenesteUrl: String = "TJENESTE_URL".configProperty(),
		val gjenopptaSoknadsArbeid: String = "GJENOPPTA_SOKNADS_ARBEID".configProperty(),
		val ettersendePaSoknad: String = "ETTERSENDE_PA_SOKNAD".configProperty(),
		val username: String = readFileAsText("/var/run/secrets/nais.io/serviceuser/username", "APPLICATION_USERNAME".configProperty()),
		val password: String = readFileAsText("/var/run/secrets/nais.io/serviceuser/password", "APPLICATION_PASSWORD".configProperty()),
		val servers: String = "KAFKA_BOOTSTRAP_SERVERS".configProperty(),
		val schemaRegistryUrl: String = "SCHEMA_REGISTRY_URL".configProperty(),
		val clientId: String = username,
		val secure: String = "KAFKA_SECURITY".configProperty(),
		val protocol: String = "KAFKA_SECPROT".configProperty(), // SASL_PLAINTEXT | SASL_SSL
		val salsmec: String = "KAFKA_SASLMEC".configProperty(), // PLAIN
		val saslJaasConfig: String = "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$username\" password=\"$password\";",
		val kafkaTopicBeskjed: String = "KAFKA_TOPIC_BESKJED".configProperty(),
		val kafkaTopicOppgave: String = "KAFKA_TOPIC_OPPGAVE".configProperty(),
		val kafkaTopicDone: String = "KAFKA_TOPIC_DONE".configProperty(),
		val publisereEndringer: Boolean = "PUBLISERE_BRUKERNOTIFIKASJONER".configProperty().toBoolean()
	)

}

@org.springframework.context.annotation.Configuration
class ConfigConfig {
	@Bean
	fun appConfiguration() = AppConfiguration()
}
