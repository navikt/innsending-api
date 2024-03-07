package no.nav.soknad.innsending.util.mapping.tilleggsstonad

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.model.DokumentSoknadDto
import java.text.SimpleDateFormat
import java.util.*
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar


fun json2Xml(
	dailyTravelingExpencesJson: JsonApplication<JsonDrivingListSubmission>,
	soknadDto: DokumentSoknadDto,
): ByteArray {

	// Map dailyTravelingExpencesJson til paaloepteUtgifter
	val paaloepteUtgifter = convertToJsonDrivingListToXML(soknadDto, dailyTravelingExpencesJson)

	// Konverter paaloepteUtgifter til xml-bytearray
	return convertXmlToByteArray(paaloepteUtgifter)
}


fun convertXmlToByteArray(paaloepteUtgifter: PaaloepteUtgifter): ByteArray {
	val xmlMapper = XmlMapper(
		JacksonXmlModule().apply {
			setDefaultUseWrapper(false)
		}
	).apply {
		enable(SerializationFeature.INDENT_OUTPUT)
		disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
	}
	xmlMapper.setDateFormat(SimpleDateFormat("yyyy-MM-ddXXX", Locale.forLanguageTag("nb")))
	xmlMapper.registerModule(JaxbAnnotationModule())
	val xml = xmlMapper.writeValueAsString(paaloepteUtgifter)
	return xml.toByteArray()
}


fun convertToJsonDrivingListToXML(
	soknadDto: DokumentSoknadDto,
	dailyTravelingExpencesJson: JsonApplication<JsonDrivingListSubmission>
): PaaloepteUtgifter {
	if (dailyTravelingExpencesJson.applicationDetails.expencePeriodes == null) throw BackendErrorException("{${soknadDto.innsendingsId}: Ingen reisekostnader spesifisert")

	return PaaloepteUtgifter(
		vedtaksId = dailyTravelingExpencesJson.applicationDetails.expencePeriodes.selectedVedtaksId,
		utgiftsperioder = convertToUtgiftsperioder(dailyTravelingExpencesJson.applicationDetails.expencePeriodes.dates)
	)
}

fun convertToUtgiftsperioder(dailyExpences: List<JsonDailyExpences>): List<Utgiftsperioder> {
	val groupByBetalingsplanId = dailyExpences.groupBy { it.betalingsplanId }
	return groupByBetalingsplanId.map {
		Utgiftsperioder(
			betalingsplanId = it.key,
			totaltParkeringsbeloep = it.value.sumOf { daily -> daily.parking ?: 0.0 }.toBigDecimal().toBigInteger(),
			totaltAntallDagerKjoert = it.value.size.toBigInteger(),
			utgiftsdagerMedParkering = convertToUtgiftsdager(it.value)
		)
	}
}

fun convertToUtgiftsdager(expences: List<JsonDailyExpences>?): List<Utgiftsdager>? {
	if (expences == null) return null
	return expences.map {
		Utgiftsdager(
			utgiftsdag = convertToXmlGregorianWithTimeZone(it.date),
			parkeringsutgift = it.parking?.toBigDecimal()?.toBigInteger()
		)
	}
}

fun convertToXmlGregorianWithTimeZone(dateString: String): XMLGregorianCalendar {

	val format = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
	val dateFormat = SimpleDateFormat(format)
	dateFormat.timeZone = TimeZone.getDefault()

	val date: Date = dateFormat.parse(dateString)

	//val targetDateFormat = SimpleDateFormat("yyyy-MM-ddXXX")
	val targetDateFormat = SimpleDateFormat("yyyy-MM-dd")
	val targetDateString: String = targetDateFormat.format(date)
	val targetDate: Date = targetDateFormat.parse(targetDateString)

	val cal = GregorianCalendar()
	cal.time = targetDate

	return DatatypeFactory.newInstance().newXMLGregorianCalendar(cal)
}

