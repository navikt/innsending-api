package no.nav.soknad

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.resilience.annotation.EnableResilientMethods
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootApplication(exclude = [SecurityAutoConfiguration::class])
@EnableTransactionManagement
@ConfigurationPropertiesScan
@EnableResilientMethods
@EnableAsync
class InnsendingApiApplication

fun main(args: Array<String>) {
	runApplication<InnsendingApiApplication>(*args)
}
