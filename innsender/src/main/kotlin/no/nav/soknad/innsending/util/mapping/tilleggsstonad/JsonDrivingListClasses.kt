package no.nav.soknad.innsending.util.mapping.tilleggsstonad

data class JsonDrivingListSubmission(
	// Dine opplysninger blir ikke brukt i forbindelse med mapping til XML, dropper derfor disse
	// Tilleggsopplysninger
	val tilleggsopplysninger: String? = null,

	// Dersom det er hentet aktivitet / maalgrupper fra Arena skal maalgruppen som har overlappende periode med hentet aktivitet sendes inn.
	val maalgruppeinformasjon: JsonMaalgruppeinformasjon? = null,

	val expensePeriodes: JsonDrivingListExpences? = null
)

data class JsonDrivingListExpences(
	val selectedVedtaksId: String,
	val tema: String? = null,
	val dates: List<JsonDailyExpences> = emptyList(),
)

data class JsonDailyExpences(
	val date: String,
	val parking: Double? = null,
	val betalingsplanId: String
)

