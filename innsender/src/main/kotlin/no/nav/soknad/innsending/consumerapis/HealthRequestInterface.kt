package no.nav.soknad.innsending.consumerapis

interface HealthRequestInterface {

	fun ping(): String

	fun isReady(): String

	fun isAlive(): String

}
