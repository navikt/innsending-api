package no.nav.soknad.innsending.exceptions

enum class ErrorCode(val code: String) {
	GENERAL_ERROR("somethingFailedTryLater"),
	NOT_FOUND("resourceNotFound"),
}
