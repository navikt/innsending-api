package no.nav.soknad.innsending.util.mapping.tilleggsstonad

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.tilleggsstonad.FyllUtJsonTestBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DrivinglistExpencesJson2XmlConverterTest {

	@Test
	fun json2XmlTest_drivingListExpences() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.10", tema = "TSR").build()
		val jsonFil = Hjelpemetoder.getBytesFromFile("/__files/kjøreliste-NAV-11-12.10-05032024.json")

		val jsonObj = convertToJsonDrivingListJson(soknadDto, jsonFil)

		val xmlFil = json2Xml(jsonObj, soknadDto)

		Assertions.assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		Assertions.assertTrue(xmlString.contains("<vedtaksId>43258684</vedtaksId>"))
		Assertions.assertTrue(
			xmlString.contains(
				"    <betalingsplanId>15573699</betalingsplanId>\n" +
					"    <totaltParkeringsbeloep>200</totaltParkeringsbeloep>\n" +
					"    <totaltAntallDagerKjoert>2</totaltAntallDagerKjoert>\n"
			)
		)

	}


	@Test
	fun `Convert to XML of drivingListExppences`() {

		val skjemanr = FyllUtJsonTestBuilder().drivingListExpencesSkjemanr
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
		Assertions.assertTrue(xmlString.contains("<utgiftsdag>2024-01-08+01:00</utgiftsdag>"))
		Assertions.assertTrue(xmlString.contains("<parkeringsutgift>150</parkeringsutgift>"))
		Assertions.assertTrue(xmlString.contains("<utgiftsdag>2024-01-09+01:00</utgiftsdag>"))
		Assertions.assertTrue(xmlString.contains("<parkeringsutgift>100</parkeringsutgift>"))
		Assertions.assertTrue(xmlString.contains("<utgiftsdag>2024-01-10+01:00</utgiftsdag>"))

	}

}
