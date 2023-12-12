package no.nav.soknad.innsending.util.mapping

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.builders.*
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.text.SimpleDateFormat
import javax.xml.stream.XMLInputFactory


class TilleggsstonadJson2XmlConverterKtTest {

	@Test
	fun json2XmlTest() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12", tema = "TSO").build()
		val jsonFil = Hjelpemetoder.getBytesFromFile("/__files/tilleggsstonad-dagligreise-m-bil.json")

		val xmlFil = json2Xml(soknadDto, jsonFil)

		assertNotNull(xmlFil)

	}


	@Test
	fun `Default case test convert to XML of daily travel excpenses`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()
		val dagligReise =
			JsonDagligReiseTestBuilder().startdatoDdMmAaaa("2023-12-01").sluttdatoDdMmAaaa("2024-06-20").build()
		val jsonReisestottesoknad = JsonReiseTestBuilder().dagligReise(dagligReise = dagligReise).build()
		val tilleggsstonad =
			JsonApplicationTestBuilder().rettighetstyper(rettighetstype = jsonReisestottesoknad).build()

		val xmlFil = json2Xml(
			soknadDto, tilleggsstonad
		)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(
			xmlString.contains(
				"      <dagligReise>\n" +
					"        <periode>\n" +
					"          <fom>2023-10-01+02:00</fom>\n" +
					"          <tom>2024-01-31+01:00</tom>\n" +
					"        </periode>\n"
			)
		)
		assertTrue(xmlString.contains("<aktivitetsadresse>Kongensgate 10, 3701</aktivitetsadresse>"))
		assertTrue(xmlString.contains("<dagligReise>"))
		assertTrue(xmlString.contains("<avstand>10.0</avstand>"))
		assertTrue(xmlString.contains("<innsendingsintervall>UKE</innsendingsintervall>"))
		assertTrue(xmlString.contains("<sumAndreUtgifter>1150.0</sumAndreUtgifter>"))
	}

	@Test
	fun `Convert to XML of daily travel excpenses - using public transport`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()
		val dagligReise = JsonDagligReiseTestBuilder()
			.kanDuReiseKollektivtDagligReise("Ja")
			.hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise(2000)
			.kanIkkeReiseKollektivtDagligReise(null)
			.build()
		val jsonReisestottesoknad = JsonReiseTestBuilder().dagligReise(dagligReise).build()
		val tilleggsstonad =
			JsonApplicationTestBuilder().rettighetstyper(rettighetstype = jsonReisestottesoknad).build()

		val xmlFil = json2Xml(
			soknadDto, tilleggsstonad
		)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("<dagligReise>"))
		assertTrue(xmlString.contains("<avstand>10.0</avstand>"))
		assertTrue(xmlString.contains("<kanOffentligTransportBrukes>true</kanOffentligTransportBrukes>"))
		assertTrue(xmlString.contains("<kanEgenBilBrukes>false</kanEgenBilBrukes>"))
		assertTrue(xmlString.contains("<beloepPerMaaned>2000</beloepPerMaaned>"))
	}


	@Test
	fun `Convert to XML of daily travel excpenses - using taxi`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()
		val dagligReise = JsonDagligReiseTestBuilder()
			.kanDuReiseKollektivtDagligReise("Nei")
			.kanBenytteEgenBil(null)
			.kanDuBenytteDrosje("Ja")
			.oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIPeriodenDuSokerOmStonadFor(6000)
			.kanIkkeBenytteEgenBil(
				KanIkkeBenytteEgenBil(
					hvaErArsakenTilAtDuIkkeKanBenytteEgenBil = "disponererIkkeBil",
					hvilkeAndreArsakerGjorAtDuIkkeKanBenytteEgenBil = null,
					kanDuBenytteDrosje = "Ja",
					oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIperiodenDuSokerOmStonadFor = 6000
				)
			)
			.build()
		val jsonReisestottesoknad = JsonReiseTestBuilder().dagligReise(dagligReise).build()
		val tilleggsstonad =
			JsonApplicationTestBuilder().rettighetstyper(rettighetstype = jsonReisestottesoknad).build()

		val xmlFil = json2Xml(
			soknadDto, tilleggsstonad
		)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("<dagligReise>"))
		assertTrue(xmlString.contains("<avstand>10.0</avstand>"))
		assertTrue(xmlString.contains("<kanOffentligTransportBrukes>false</kanOffentligTransportBrukes>"))
		assertTrue(xmlString.contains("<aarsakTilIkkeOffentligTransport>Få og upraktiske tidspunkt for avganger</aarsakTilIkkeOffentligTransport>"))
		assertTrue(xmlString.contains("<kanEgenBilBrukes>false</kanEgenBilBrukes>"))
		assertTrue(
			xmlString.contains(
				"          <drosjeTransportutgifter>\n" +
					"            <beloep>6000</beloep>\n" +
					"          </drosjeTransportutgifter>\n"
			)
		)
	}

	@Test
	fun `Default case test convert to XML of meeting conventions travel excpenses`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()
		val reiseSamling =
			JsonReiseSamlingTestBuilder().build()
		val jsonReisestottesoknad = JsonReiseTestBuilder().samling(reiseSamling).build()
		val tilleggsstonad =
			JsonApplicationTestBuilder().rettighetstyper(rettighetstype = jsonReisestottesoknad).build()

		val xmlFil = json2Xml(
			soknadDto, tilleggsstonad
		)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("<reiseObligatoriskSamling>"))
		assertTrue(xmlString.contains("<avstand>120</avstand>"))
		assertTrue(
			xmlString.contains(
				"      <reiseObligatoriskSamling>\n" +
					"        <periode>\n" +
					"          <fom>2024-01-02+01:00</fom>\n" +
					"          <tom>2024-02-07+01:00</tom>\n" +
					"        </periode>\n"
			)
		)
		assertTrue(xmlString.contains("<beloepPerMaaned>1000</beloepPerMaaned>"))

	}

	@Test
	fun `Default case test convert to XML of start and end of activity excpenses`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()
		val oppstartOgAvslutningAvAktivitet =
			JsonReiseOppstartSluttTestBuilder().build()
		val jsonReisestottesoknad = JsonReiseTestBuilder().startAvslutning(oppstartOgAvslutningAvAktivitet).build()
		val tilleggsstonad =
			JsonApplicationTestBuilder().rettighetstyper(rettighetstype = jsonReisestottesoknad).build()

		val xmlFil = json2Xml(
			soknadDto, tilleggsstonad
		)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("<reiseVedOppstartOgAvsluttetAktivitet>"))
		assertTrue(xmlString.contains("<avstand>100</avstand>"))
		assertTrue(xmlString.contains("<antallReiser>4</antallReiser>"))
		assertTrue(xmlString.contains("<beloepPerMaaned>3000</beloepPerMaaned>"))

	}


	@Test
	fun `Case test convert to XML of start and end of activity excpenses -other expences `() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()
		val oppstartOgAvslutningAvAktivitet =
			JsonReiseOppstartSluttTestBuilder().hvilkeUtgifterHarDuIForbindelseMedReisen4(99999).build()
		val jsonReisestottesoknad = JsonReiseTestBuilder().startAvslutning(oppstartOgAvslutningAvAktivitet).build()
		val tilleggsstonad =
			JsonApplicationTestBuilder().rettighetstyper(rettighetstype = jsonReisestottesoknad).build()

		val xmlFil = json2Xml(
			soknadDto, tilleggsstonad
		)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("<reiseVedOppstartOgAvsluttetAktivitet>"))
		assertTrue(xmlString.contains("<avstand>100</avstand>"))
		assertTrue(xmlString.contains("<antallReiser>4</antallReiser>"))
		assertTrue(xmlString.contains("<beloepPerMaaned>99999</beloepPerMaaned>"))

	}

	@Test
	fun `Default case test convert to XML of job applying excpenses`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()
		val reiseArbeidssoker =
			JsonReiseArbeidssokerTestBuilder().build()
		val jsonReisestottesoknad = JsonReiseTestBuilder().reiseArbeidssoker(reiseArbeidssoker).build()
		val tilleggsstonad =
			JsonApplicationTestBuilder().rettighetstyper(rettighetstype = jsonReisestottesoknad).build()

		val xmlFil = json2Xml(
			soknadDto, tilleggsstonad
		)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("<reisestoenadForArbeidssoeker>"))
		assertTrue(xmlString.contains("<beloepPerMaaned>5000</beloepPerMaaned>"))
		/*
				val xmlMapper = getXmlMapper()
				val tilleggsstonadXml = xmlMapper.readValue(xmlFil, Tilleggsstoenadsskjema::class.java)
				assertTrue(tilleggsstonadXml.rettighetstype.reiseutgifter != null)
		*/

	}


	@Test
	fun `Convert to XML of combined travel excpenses`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()
		val startOgSluttPaAktivitet =
			JsonReiseOppstartSluttTestBuilder().startdatoDdMmAaaa1("2023-11-30").sluttdatoDdMmAaaa1("2024-06-21").build()
		val dagligReise =
			JsonDagligReiseTestBuilder().startdatoDdMmAaaa("2023-12-01").sluttdatoDdMmAaaa("2024-06-20").build()
		val jsonReisestottesoknad = JsonReiseTestBuilder().dagligReise(dagligReise = dagligReise)
			.startAvslutning(startAvslutning = startOgSluttPaAktivitet).build()
		val tilleggsstonad =
			JsonApplicationTestBuilder().rettighetstyper(rettighetstype = jsonReisestottesoknad).build()

		val xmlFil = json2Xml(
			soknadDto, tilleggsstonad
		)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("<aktivitetsadresse>Kongensgate 10, 3701</aktivitetsadresse>"))
		assertTrue(xmlString.contains("<dagligReise>"))
		assertTrue(xmlString.contains("<avstand>10.0</avstand>"))
		assertTrue(xmlString.contains("<innsendingsintervall>UKE</innsendingsintervall>"))
		assertTrue(xmlString.contains("<sumAndreUtgifter>1150.0</sumAndreUtgifter>"))
		assertTrue(xmlString.contains("<reiseVedOppstartOgAvsluttetAktivitet>"))
		assertTrue(xmlString.contains("<avstand>100</avstand>"))
		assertTrue(xmlString.contains("<antallReiser>4</antallReiser>"))
		assertTrue(xmlString.contains("<beloepPerMaaned>3000</beloepPerMaaned>"))
	}

	@Test
	fun `Default case test convert to XML child care expenses`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()
		val barnePass = JsonBarnePassTestBuilder().build()
		val tilleggsstonad =
			JsonApplicationTestBuilder().rettighetstyper(rettighetstype = barnePass).build()
		val xmlFil = json2Xml(soknadDto, tilleggsstonad)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("tilsynsutgifterBarn"))
		assertTrue(xmlString.contains("<maanedligUtgiftTilsynBarn>4000</maanedligUtgiftTilsynBarn>"))

		/*
				val xmlMapper = getXmlMapper()
				val tilleggsstonadXml = xmlMapper.readValue(xmlFil, Tilleggsstoenadsskjema::class.java)
				assertEquals(
					tilleggsstonad.tilleggsstonad.rettighetstype?.tilsynsutgifter?.fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa,
					tilleggsstonadXml.rettighetstype.tilsynsutgifter?.tilsynsutgifterBarn?.annenForsoergerperson
				)
		*/

	}

	@Test
	fun `Case test convert to XML child care expenses - two children`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()
		val barnePass = JsonBarnePassTestBuilder()
			.barnePass(
				listOf(
					BarnePass(
						fornavn = "Fnavn",
						etternavn = "Enavn",
						fodselsdatoDdMmAaaa = "2020-04-03",
						jegSokerOmStonadTilPassAvDetteBarnet = "Ja",
						sokerStonadForDetteBarnet = SokerStonadForDetteBarnet(
							hvemPasserBarnet = "Barnet mitt får pass av dagmamma eller dagpappa",
							oppgiManedligUtgiftTilBarnepass = 4000,
							harBarnetFullfortFjerdeSkolear = "Nei",
							hvaErArsakenTilAtBarnetDittTrengerPass = null
						)
					),
					BarnePass(
						fornavn = "Fnavn2",
						etternavn = "Enavn",
						fodselsdatoDdMmAaaa = "2019-04-03",
						jegSokerOmStonadTilPassAvDetteBarnet = "Ja",
						sokerStonadForDetteBarnet = SokerStonadForDetteBarnet(
							hvemPasserBarnet = "Barnet mitt får pass av dagmamma eller dagpappa",
							oppgiManedligUtgiftTilBarnepass = 3500,
							harBarnetFullfortFjerdeSkolear = "Nei",
							hvaErArsakenTilAtBarnetDittTrengerPass = null
						)
					)
				)
			)
			.build()
		val tilleggsstonad =
			JsonApplicationTestBuilder().rettighetstyper(rettighetstype = barnePass).build()
		val xmlFil = json2Xml(soknadDto, tilleggsstonad)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("tilsynsutgifterBarn"))
		assertTrue(
			xmlString.contains(
				"        <barn>\n" +
					"          <personidentifikator>030419</personidentifikator>\n" +
					"          <tilsynskategori>KOM</tilsynskategori>\n" +
					"          <navn>Fnavn2 Enavn</navn>\n" +
					"          <harFullfoertFjerdeSkoleaar>false</harFullfoertFjerdeSkoleaar>\n" +
					"          <maanedligUtgiftTilsynBarn>3500</maanedligUtgiftTilsynBarn>\n" +
					"        </barn>\n"
			)
		)

		/*
				val xmlMapper = getXmlMapper()
				val tilleggsstonadXml = xmlMapper.readValue(xmlFil, Tilleggsstoenadsskjema::class.java)
				assertEquals(
					tilleggsstonad.tilleggsstonad.rettighetstype?.tilsynsutgifter?.fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa,
					tilleggsstonadXml.rettighetstype.tilsynsutgifter?.tilsynsutgifterBarn?.annenForsoergerperson
				)
		*/

	}

	@Test
	fun `Default case test convert to XML housing expenses`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()
		val boStotte = JsonBostotteTestBuilder().build()
		val tilleggsstonad =
			JsonApplicationTestBuilder().rettighetstyper(rettighetstype = boStotte).build()
		val xmlFil = json2Xml(
			soknadDto, tilleggsstonad
		)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("<boutgifter>"))
		assertTrue(xmlString.contains("<boutgifterHjemstedAktuell>8000</boutgifterHjemstedAktuell>"))

		/*
		val xmlMapper = getXmlMapper()
		val tilleggsstonadXml = xmlMapper.readValue(xmlFil, Tilleggsstoenadsskjema::class.java)
		assertEquals(
			boStotte.bostotte?.hvilkeBoutgifterSokerDuOmAFaDekket,
			tilleggsstonadXml.rettighetstype.boutgifter?.bostoetteBeloep?.toString()
		)
		*/
	}

	@Test
	fun `Default case test convert to XML learning material expenses`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()
		val laerestotte = JsonLaeremiddelTestBuilder().build()
		val tilleggsstonad =
			JsonApplicationTestBuilder().rettighetstyper(rettighetstype = laerestotte).build()
		val xmlFil = json2Xml(soknadDto, tilleggsstonad)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("laeremiddelutgifter"))
		assertTrue(xmlString.contains("<beloep>6000</beloep>"))
		/*
				val xmlMapper = getXmlMapper()
				val tilleggsstonadXml = xmlMapper.readValue(xmlFil, Tilleggsstoenadsskjema::class.java)
				assertTrue(tilleggsstonadXml.rettighetstype.laeremiddelutgifter != null)
		*/

	}


	@Test
	fun `Default case test convert to XML moving expenses`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()

		val flytteutgifter = JsonFlyttingTestBuilder().build()
		val tilleggsstonad =
			JsonApplicationTestBuilder().rettighetstyper(rettighetstype = flytteutgifter).build()
		val xmlFil = json2Xml(soknadDto, tilleggsstonad)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("flytteutgifter"))
		assertTrue(xmlString.contains("<flytterSelv>true</flytterSelv>"))
		assertTrue(xmlString.contains("<sumTilleggsutgifter>1200.0</sumTilleggsutgifter>"))

	}

	@Test
	fun `Convert to XML moving expenses - Paying for transportation`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()

		val flytteutgifter = JsonFlyttingTestBuilder()
			.ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra("Jeg vil bruke flyttebyrå")
			.jegVilBrukeFlyttebyra(
				JegVilBrukeFlyttebyra(
					navnPaFlyttebyra1 = "Flytte1",
					belop = 4000,
					navnPaFlyttebyra2 = "Flytte2",
					belop1 = 5000,
					jegVelgerABruke = "Flyttebyrå 1"
				)
			)
			.jegFlytterSelv(jegFlytterSelv = null)
			.build()

		val tilleggsstonad =
			JsonApplicationTestBuilder().rettighetstyper(rettighetstype = flytteutgifter).build()
		val xmlFil = json2Xml(soknadDto, tilleggsstonad)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("flytteutgifter"))
		assertTrue(xmlString.contains("<flytterSelv>false</flytterSelv>"))
		assertTrue(xmlString.contains("<sumTilleggsutgifter>4000.0</sumTilleggsutgifter>"))

	}

	@Test
	fun `Convert to XML moving expenses - Chosen to move by myself after receiving offers`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()

		val flytteutgifter = JsonFlyttingTestBuilder()
			.ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra("Jeg har innhentet tilbud fra minst to flyttebyråer, men velger å flytte selv")
			.jegVilBrukeFlyttebyra(jegVilBrukeFlyttebyra = null)
			.jegFlytterSelv(jegFlytterSelv = null)
			.jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv(
				JegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv(
					navnPaFlyttebyra1 = "Flytte1",
					belop = 4000,
					navnPaFlyttebyra2 = "Flytte2",
					belop1 = 5000,
					hvorLangtSkalDuFlytte1 = 130,
					hengerleie = 1000,
					200,
					parkering = 200,
					ferje = 0,
					annet = null
				)
			)
			.build()

		val tilleggsstonad =
			JsonApplicationTestBuilder().rettighetstyper(rettighetstype = flytteutgifter).build()
		val xmlFil = json2Xml(soknadDto, tilleggsstonad)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("flytteutgifter"))
		assertTrue(xmlString.contains("<flytterSelv>true</flytterSelv>"))
		assertTrue(xmlString.contains("<sumTilleggsutgifter>1400.0</sumTilleggsutgifter>"))

	}


	private fun getXmlMapper(): XmlMapper {
		val inputFactory = XMLInputFactory.newFactory()
		inputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false)
		val kotlinModule = KotlinModule.Builder()
			.disable(KotlinFeature.StrictNullChecks)
			.build()
		val xmlMapper = XmlMapper(inputFactory)
		xmlMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
		xmlMapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
		xmlMapper.setDateFormat(SimpleDateFormat("yyyy-MM-ddXXX"))
		xmlMapper.registerModule(kotlinModule)
		return xmlMapper
	}

}
