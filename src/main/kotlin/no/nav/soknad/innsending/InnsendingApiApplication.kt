package no.nav.soknad.innsending

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
open class InnsendingApiApplication

fun main(args: Array<String>) {
    runApplication<InnsendingApiApplication>(*args)
}
