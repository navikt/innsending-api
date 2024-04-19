package no.nav.soknad.innsending.util.mapping.tilleggsstonad

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import java.math.BigInteger
import javax.xml.datatype.XMLGregorianCalendar

@JacksonXmlRootElement(localName = "paaloepteUtgifter")
@JsonPropertyOrder("vedtaksId", "utgiftsperioder")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class PaaloepteUtgifter(
	@JacksonXmlProperty(localName = "vedtaksId")
	val vedtaksId: String? = null,
	@JacksonXmlProperty(localName = "utgiftsperioder")
	val utgiftsperioder: List<Utgiftsperioder>? = null
)

@JacksonXmlRootElement(localName = "Utgiftsperioder")
@JsonPropertyOrder("betalingsplanId", "totaltParkeringsbeloep", "totaltAntallDagerKjoert", "utgiftsdagerMedParkering")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Utgiftsperioder(

	@JacksonXmlProperty(localName = "betalingsplanId")
	val betalingsplanId: String? = null,

	@JacksonXmlProperty(localName = "totaltParkeringsbeloep")
	val totaltParkeringsbeloep: BigInteger? = null,

	@JacksonXmlProperty(localName = "totaltAntallDagerKjoert")
	val totaltAntallDagerKjoert: BigInteger? = null,

	@JacksonXmlProperty(localName = "utgiftsdagerMedParkering")
	val utgiftsdagerMedParkering: List<Utgiftsdager>? = null
)


@JacksonXmlRootElement(localName = "Utgiftsdager")
@JsonPropertyOrder("utgiftsdag", "parkeringsutgift")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Utgiftsdager(

	@JacksonXmlProperty(localName = "date")
	val utgiftsdag: String? = null,

	@JacksonXmlProperty(localName = "parkeringsutgift")
	val parkeringsutgift: BigInteger? = null

)
