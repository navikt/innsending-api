package no.nav.soknad.innsending.util.mapping.tilleggsstonad

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.tilleggsstonad.FyllUtJsonTestBuilder
import org.junit.Test
import org.junit.jupiter.api.Assertions
import kotlin.test.assertTrue

class DrivinglistExpencesJson2JsonConverterTest {


	@Test
	fun happyCase() {

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
				Dates(date = "2024-01-08T00:00:00.000Z", parking = "125", betalingsplanId = "22"),
				Dates(date = "2024-01-09T00:00:00.000Z", parking = "125", betalingsplanId = "22"),
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

		assertTrue(strukturertJson != null)
		Assertions.assertEquals(vedtaksId, strukturertJson.applicationDetails.expensePeriodes?.selectedVedtaksId)
		Assertions.assertEquals(
			drivingListExpences.dates.size,
			strukturertJson.applicationDetails.expensePeriodes?.dates?.size
		)

	}

}
