package no.nav.soknad

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.retry.annotation.EnableRetry
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootApplication(exclude = [SecurityAutoConfiguration::class])
@EnableTransactionManagement
@ConfigurationPropertiesScan
@EnableRetry
class InnsendingApiApplication

fun main(args: Array<String>) {
	runApplication<InnsendingApiApplication>(*args)
}
