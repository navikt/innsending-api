package no.nav.soknad.innsending.consumerapis.antivirus

interface AntivirusInterface {
	fun scan(file: ByteArray): Boolean
}
