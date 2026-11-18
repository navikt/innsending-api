package no.nav.soknad

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.resilience.annotation.EnableResilientMethods
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.RollbackOn

@SpringBootApplication()
@EnableTransactionManagement(rollbackOn = RollbackOn.ALL_EXCEPTIONS)
@ConfigurationPropertiesScan
@EnableResilientMethods
@EnableAsync
class InnsendingApiApplication

fun main(args: Array<String>) {
	runApplication<InnsendingApiApplication>(*args)
}
