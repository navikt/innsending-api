package no.nav.soknad.innsending.util.mapping

import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class TilleggsstonadJson2XmlConverterKtTest {

	@Test
	fun json2XmlTest() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.13", tema = "TSO").build()
		val jsonFil = Hjelpemetoder.getBytesFromFile("/__files/tilleggsstonad-dagligreise-m-bil.json")

		val xmlFil = json2Xml(soknadDto, jsonFil)

		assertNotNull(xmlFil)

	}

}
