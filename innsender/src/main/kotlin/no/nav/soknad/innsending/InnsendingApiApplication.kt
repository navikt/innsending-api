package no.nav.soknad.innsending

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootApplication (exclude = arrayOf(SecurityAutoConfiguration::class))
@EnableTransactionManagement
@ConfigurationPropertiesScan
class InnsendingApiApplication

fun main(args: Array<String>) {
	runApplication<InnsendingApiApplication>(*args)
}
