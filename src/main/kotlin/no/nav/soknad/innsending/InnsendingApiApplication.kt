package no.nav.soknad.innsending

import no.nav.soknad.innsending.config.DBConfig
import no.nav.soknad.innsending.config.KafkaConfig
import no.nav.soknad.innsending.config.RestConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootApplication
@EnableTransactionManagement
@ConfigurationPropertiesScan
@EnableConfigurationProperties(DBConfig::class, KafkaConfig::class, RestConfig::class)
class InnsendingApiApplication

fun main(args: Array<String>) {
	runApplication<InnsendingApiApplication>(*args)
}
