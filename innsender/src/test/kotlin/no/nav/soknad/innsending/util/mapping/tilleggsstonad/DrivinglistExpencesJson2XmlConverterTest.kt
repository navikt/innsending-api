package no.nav.soknad.innsending.util.mapping.tilleggsstonad

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.tilleggsstonad.FyllUtJsonTestBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.text.SimpleDateFormat
import java.util.*

class DrivinglistExpencesJson2XmlConverterTest {

	@Test
	fun json2XmlTest_drivingListExpences_flereBetalingsplaner() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = kjoreliste, tema = "TSR").build()
		val jsonFil = Hjelpemetoder.getBytesFromFile("/__files/refusjondagligreise-NAV-11-12.24B-19042024.json")

		val jsonObj = convertToJsonDrivingListJson(soknadDto, jsonFil)

		val xmlFil = json2Xml(jsonObj, soknadDto)

		Assertions.assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		Assertions.assertTrue(xmlString.contains("<vedtaksId>36989400</vedtaksId>"))
		Assertions.assertTrue(xmlString.contains("<betalingsplanId>14732303</betalingsplanId>"))
		Assertions.assertTrue(xmlString.contains("<totaltParkeringsbeloep>46</totaltParkeringsbeloep>"))
		Assertions.assertTrue(xmlString.contains("<totaltAntallDagerKjoert>2</totaltAntallDagerKjoert>"))
	}


	@Test
	fun json2XmlTest_drivingListExpences_enBetalingsplan() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = kjoreliste, tema = "TSO").build()
		val jsonFil = Hjelpemetoder.getBytesFromFile("/__files/kjøreliste-NAV-11-12.24B-26032024.json")

		val jsonObj = convertToJsonDrivingListJson(soknadDto, jsonFil)

		val xmlFil = json2Xml(jsonObj, soknadDto)

		Assertions.assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		Assertions.assertTrue(xmlString.contains("<vedtaksId>36989399</vedtaksId>"))
		Assertions.assertTrue(xmlString.contains("<betalingsplanId>14732295</betalingsplanId>"))
		Assertions.assertTrue(xmlString.contains("<totaltParkeringsbeloep>0</totaltParkeringsbeloep>"))
		Assertions.assertTrue(xmlString.contains("<totaltAntallDagerKjoert>19</totaltAntallDagerKjoert>"))
	}

	@Test
	fun `Convert to XML of drivingListExppences`() {

		val skjemanr = kjoreliste
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = skjemanr, tema = "TSR").build()
		val language = "no-Nb"
		val vedtaksId = "12345678"

		val drivingListExpences = Drivinglist(
			selectedVedtaksId = vedtaksId,
			dates = listOf(
				Dates(date = "2024-01-01T00:00:00.000Z", parking = "125", betalingsplanId = "21"),
				Dates(date = "2024-01-02T00:00:00.000Z", parking = "125", betalingsplanId = "21"),
				Dates(date = "2024-01-03T00:00:00.000Z", parking = "", betalingsplanId = "21"),
				Dates(date = "2024-01-08T00:00:00.000Z", parking = "150", betalingsplanId = "22"),
				Dates(date = "2024-01-09T00:00:00.000Z", parking = "100", betalingsplanId = "22"),
				Dates(date = "2024-01-10T00:00:00.000Z", parking = "", betalingsplanId = "22"),
				Dates(date = "2024-01-15T00:00:00.000Z", parking = "125", betalingsplanId = "23"),
				Dates(date = "2024-01-16T00:00:00.000Z", parking = "125", betalingsplanId = "23"),
				Dates(date = "2024-01-17T00:00:00.000Z", parking = "", betalingsplanId = "23"),
				Dates(date = "2024-01-22T00:00:00.000Z", parking = "125", betalingsplanId = "24"),
				Dates(date = "2024-01-23T00:00:00.000Z", parking = "125", betalingsplanId = "24"),
				Dates(date = "2024-01-240T00:00:00.000Z", parking = "", betalingsplanId = "24"),
			)
		)

		val fyllUtObj = FyllUtJsonTestBuilder()
			.language(language)
			.skjemanr(skjemanr)
			.arenaAktivitetOgMaalgruppe(
				maalgruppe = null,
				aktivitetId = null,

				SkjemaPeriode("2024-01-02", "2024-03-30")
			)
			.periode(null, null)
			.drivingListExpences(drivingListExpences)
			.build()

		val mapper = jacksonObjectMapper()
		val fyllUtJson = mapper.writeValueAsString(fyllUtObj)
		val strukturertJson =
			convertToJsonDrivingListJson(soknadDto, fyllUtJson.toString().toByteArray())

		val xmlByteArray = json2Xml(strukturertJson, soknadDto)

		val xmlString = xmlByteArray.decodeToString()
		System.out.println(xmlString)

		Assertions.assertNotNull(xmlString)
		Assertions.assertTrue(xmlString.contains("<vedtaksId>$vedtaksId</vedtaksId>"))
		Assertions.assertTrue(xmlString.contains("<utgiftsperioder>"))
		Assertions.assertTrue(xmlString.contains("<betalingsplanId>21</betalingsplanId>"))
		Assertions.assertTrue(xmlString.contains("<betalingsplanId>22</betalingsplanId>"))
		Assertions.assertTrue(xmlString.contains("<betalingsplanId>23</betalingsplanId>"))
		Assertions.assertTrue(xmlString.contains("<betalingsplanId>24</betalingsplanId>"))
		Assertions.assertTrue(xmlString.contains("<utgiftsdagerMedParkering>"))
		Assertions.assertTrue(xmlString.contains("<totaltParkeringsbeloep>250</totaltParkeringsbeloep>"))
		Assertions.assertTrue(xmlString.contains("<totaltAntallDagerKjoert>3</totaltAntallDagerKjoert>"))
		Assertions.assertTrue(xmlString.contains("<utgiftsdag>2024-01-08"))
		Assertions.assertTrue(xmlString.contains("+01:00</utgiftsdag>"))
		Assertions.assertTrue(xmlString.contains("<parkeringsutgift>150</parkeringsutgift>"))
		Assertions.assertTrue(xmlString.contains("<utgiftsdag>2024-01-09+01:00</utgiftsdag>"))
		Assertions.assertTrue(xmlString.contains("<parkeringsutgift>100</parkeringsutgift>"))
		Assertions.assertTrue(xmlString.contains("<utgiftsdag>2024-01-10+01:00</utgiftsdag>"))

	}


	@Test
	fun convertToGregorianDateWithTimeZoneTest() {
		val inputDateString = "2024-01-01T00:00:00.000Z"
		val convertedDate = convertToXmlGregorianWithTimeZone(inputDateString)
		val xmlMapper = XmlMapper(
			JacksonXmlModule().apply {
				setDefaultUseWrapper(false)
			}
		).apply {
			enable(SerializationFeature.INDENT_OUTPUT)
			disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
		}
		xmlMapper.setDateFormat(SimpleDateFormat("yyyy-MM-ddXXX", Locale.of("nb", "NO")))
		xmlMapper.registerModule(JaxbAnnotationModule())
		val xml = xmlMapper.writeValueAsString(convertedDate)

		Assertions.assertEquals("\n<XMLGregorianCalendarImpl>2024-01-01+01:00</XMLGregorianCalendarImpl>", xml)
	}

}
