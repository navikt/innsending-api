package no.nav.soknad.innsending.util.mapping.tilleggsstonad

import com.fasterxml.jackson.annotation.JsonProperty

data class JsonDrivingListSubmission(
	// Dine opplysninger
	val fornavnSoker: String,
	val etternavnSoker: String,
	@JsonProperty("harDuNorskFodselsnummerEllerDNummer")
	val harDuNorskFodselsnummerEllerDnummer: String, // ja|nei
	@JsonProperty("fodselsnummerDNummerSoker")
	val fodselsnummerDnummerSoker: String? = null,

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

