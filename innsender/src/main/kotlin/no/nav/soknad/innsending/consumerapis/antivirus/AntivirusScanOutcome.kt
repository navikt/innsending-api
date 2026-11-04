package no.nav.soknad.innsending.consumerapis.antivirus

data class AntivirusScanOutcome(
	val result: AntivirusScanResult,
	val failureReason: AntivirusScanFailureReason = if (result == AntivirusScanResult.ERROR) {
		AntivirusScanFailureReason.SCANNER_ERROR
	} else {
		AntivirusScanFailureReason.NONE
	},
	val httpStatus: Int? = null,
	val exceptionType: String? = null,
) {
	val retryable: Boolean get() = result == AntivirusScanResult.ERROR && failureReason.retryable
}

enum class AntivirusScanFailureReason(val metricValue: String, val retryable: Boolean = false) {
	NONE("none"),
	HTTP_SERVER_ERROR("http_server_error", true),
	HTTP_ERROR("http_error"),
	TIMEOUT("timeout", true),
	CONNECTION_ERROR("connection_error", true),
	CONNECTION_REJECTED("connection_rejected"),
	INVALID_RESPONSE("invalid_response"),
	SCANNER_ERROR("scanner_error"),
	INTERRUPTED("interrupted"),
	UNEXPECTED_ERROR("unexpected_error"),
}
