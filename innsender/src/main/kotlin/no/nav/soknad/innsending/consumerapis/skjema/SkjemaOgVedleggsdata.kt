package no.nav.soknad.innsending.consumerapis.skjema

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
class SkjemaOgVedleggsdata {
	@JsonProperty("Skjemanummer")
	var skjemanummer: String? = null

	@JsonProperty("Vedleggsid")
	val vedleggsid: String? = null

	@JsonProperty("Tema")
	var tema: String? = null

	@JsonProperty("Tittel")
	var tittel_no: String? = null

	@JsonProperty("Lenke")
	val url_no: String? = null

	@JsonProperty("Lenke nynorsk skjema")
	val url_nn: String? = null

	@JsonProperty("Tittel_nn")
	var tittel_nn: String? = null

	@JsonProperty("Lenke engelsk skjema")
	val url_en: String? = null

	@JsonProperty("Tittel_en")
	var tittel_en: String? = null

	@JsonProperty("Lenke samisk skjema")
	val url_se: String? = null

	@JsonProperty("Lenke tysk skjema")
	val url_de: String? = null

	@JsonProperty("Lenke fransk skjema")
	val url_fr: String? = null

	@JsonProperty("Lenke spansk skjema")
	val url_es: String? = null

	@JsonProperty("Lenke polsk skjema")
	val url_pl: String? = null

	override fun toString(): String {
		return "skjemanummer='" + skjemanummer + '\'' +
			", vedleggsid='" + vedleggsid + '\'' +
			", tittel='" + tittel_no + '\''
	}
}
