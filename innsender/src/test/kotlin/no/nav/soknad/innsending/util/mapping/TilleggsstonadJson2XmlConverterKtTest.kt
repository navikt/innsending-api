package no.nav.soknad.innsending.util.mapping

import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.builders.*
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class TilleggsstonadJson2XmlConverterKtTest {

	@Test
	fun json2XmlTest() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12", tema = "TSO").build()
		val jsonFil = Hjelpemetoder.getBytesFromFile("/__files/tilleggsstonad-dagligreise-m-bil.json")

		val xmlFil = json2Xml(soknadDto, jsonFil)

		assertNotNull(xmlFil)

	}


	@Test
	fun `test convert to XML of travel excpenses`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()
		val dagligReise =
			JsonDagligReiseTestBuilder(startdatoDdMmAaaa = "01012024", sluttdatoDdMmAaaa = "200620204").build()
		val jsonReisestottesoknad = JsonReiseTestBuilder(dagligReise = dagligReise).build()
		val tilleggsstonad =
			JsonApplicationTestBuilder(rettighetstyper = jsonReisestottesoknad).build()

		val xmlFil = json2Xml(
			soknadDto, tilleggsstonad
		)

		assertNotNull(xmlFil)

	}

	@Test
	fun `test convert to XML child care expenses`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()
		val barnePass = JsonBarnePassTestBuilder(fradato = "2024-01-01", tildato = "2024-06-30").build()
		val tilleggsstonad =
			JsonApplicationTestBuilder(rettighetstyper = barnePass).build()
		val xmlFil = json2Xml(soknadDto, tilleggsstonad)

		assertNotNull(xmlFil)

	}

	@Test
	fun `test convert to XML housing expenses`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()
		val boStotte = JsonBostotteTestBuilder(fradato = "2024-01-01", tildato = "2024-06-30").build()
		val tilleggsstonad =
			JsonApplicationTestBuilder(rettighetstyper = boStotte).build()
		val xmlFil = json2Xml(
			soknadDto, tilleggsstonad
		)

		assertNotNull(xmlFil)

	}

	@Test
	fun `test convert to XML learning material expenses`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()
		val laerestotte = JsonLaeremiddelTestBuilder(fradato = "2024-01-01", tildato = "2024-06-30").build()
		val tilleggsstonad =
			JsonApplicationTestBuilder(rettighetstyper = laerestotte).build()
		val xmlFil = json2Xml(soknadDto, tilleggsstonad)

		assertNotNull(xmlFil)

	}


	@Test
	fun `test convert to XML moving expenses`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()
		val flytteutgifter = JsonFlyttingTestBuilder(
			fradato = "2024-01-01",
			tildato = "2024-06-30",
			narFlytterDuDdMmAaaa = "2023-12-29",
			oppgiForsteDagINyJobbDdMmAaaa = "2024-01-02"
		).build()
		val tilleggsstonad =
			JsonApplicationTestBuilder(rettighetstyper = flytteutgifter).build()
		val xmlFil = json2Xml(soknadDto, tilleggsstonad)

		assertNotNull(xmlFil)

	}
}
