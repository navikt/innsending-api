package no.nav.soknad.innsending.consumerapis.antivirus

interface AntivirusInterface {
	fun scan(file: ByteArray): AntivirusScanResult

	fun scanAttempt(file: ByteArray): AntivirusScanOutcome = AntivirusScanOutcome(scan(file))
}
