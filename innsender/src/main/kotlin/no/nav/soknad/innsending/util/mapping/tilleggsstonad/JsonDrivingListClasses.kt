package no.nav.soknad.innsending.util.mapping.tilleggsstonad

data class JsonDrivingListSubmission(
	val expencePeriodes: JsonDrivingListExpences? = null
)

data class JsonDrivingListExpences(
	val selectedVedtaksId: String,
	val dates: List<JsonDailyExpences> = emptyList(),
)

data class JsonDailyExpences(
	val date: String,
	val parking: Double? = null,
	val betalingsplanId: String
)

