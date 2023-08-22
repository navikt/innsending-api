package no.nav.soknad.innsending.exceptions

// En samling av alle error codes som frontend klienter kan bruke for å vise brukervennlige feilmeldinger
enum class ErrorCode(val code: String) {
	// General Errors
	GENERAL_ERROR("somethingFailedTryLater"),
	NOT_FOUND("resourceNotFound"),

	// Illegal Actions
	APPLICATION_SENT_IN_OR_DELETED("illegalAction.applicationSentInOrDeleted"),
	FILE_CANNOT_BE_READ("illegalAction.fileCannotBeRead"),
	SEND_IN_ERROR_NO_APPLICATION("illegalAction.sendInErrorNoApplication"),
	SEND_IN_ERROR_NO_CHANGE("illegalAction.sendInErrorNoChange"),
	NOT_SUPPORTED_FILE_FORMAT("illegalAction.notSupportedFileFormat"),
	FILE_SIZE_SUM_TOO_LARGE("illegalAction.fileSizeSumTooLarge"),
	VEDLEGG_FILE_SIZE_SUM_TOO_LARGE("illegalAction.vedleggFileSizeSumTooLarge"),
	TITLE_STRING_TOO_LONG("illegalAction.titleStringTooLong"), // (ikke i bruk)
	VIRUS_SCAN_FAILED("illegalAction.virusScanFailed"),

}
