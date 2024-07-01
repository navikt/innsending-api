package no.nav.soknad.innsending.supervision.counters

enum class MethodResult(val code: String) {
	CODE_OK("OK"),
	CODE_4XX("4XX"),
	CODE_5XX("5XX")
}
