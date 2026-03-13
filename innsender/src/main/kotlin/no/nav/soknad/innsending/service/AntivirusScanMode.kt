package no.nav.soknad.innsending.service

enum class AntivirusScanMode(
	val value: String
) {
	SYNCHRONOUS("sync"),
	ASYNCHRONOUS("async"),
}
