package no.nav.soknad.antivirus

data class ScanResult(
	val filename: String,
	val result: ClamAvResult
)

enum class ClamAvResult {
	FOUND, OK, ERROR
}
