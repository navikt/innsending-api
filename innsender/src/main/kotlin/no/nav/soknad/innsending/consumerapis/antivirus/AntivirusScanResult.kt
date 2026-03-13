package no.nav.soknad.innsending.consumerapis.antivirus

enum class AntivirusScanResult(
	val metricValue: String
) {
	OK("ok"),
	FOUND("found"),
	ERROR("error"),
}
