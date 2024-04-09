package no.nav.soknad.innsending.util.mapping.tilleggsstonad

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import no.nav.soknad.innsending.utils.Date
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.tilleggsstonad.*
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import javax.xml.stream.XMLInputFactory

class TilleggsstonadJson2XmlConverterTest {

	@Test
	fun json2XmlTest_dagligReise() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.21B", tema = "TSO").build()
		val jsonFil = Hjelpemetoder.getBytesFromFile("/__files/dagligreise-NAV-11-12.21B-08032024.json")

		val xmlFil = json2Xml(soknadDto, jsonFil)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("<reiseutgifter"))
		assertTrue(xmlString.contains("<dagligReise"))

	}

	@Test
	fun json2XmlTest_samling() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.17B", tema = "TSO").build()
		val jsonFil = Hjelpemetoder.getBytesFromFile("/__files/samling-NAV-11-12.17B-22032024.json")

		val xmlFil = json2Xml(soknadDto, jsonFil)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("<reiseutgifter"))
		assertTrue(xmlString.contains("<reiseObligatoriskSamling"))

	}

	@Test
	fun json2XmlTest_reise_for_arbeid() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.22B", tema = "TSR").build()
		val jsonFil = Hjelpemetoder.getBytesFromFile("/__files/reise-for-arbeid-NAV-11-12.22B-18032024.json")

		val xmlFil = json2Xml(soknadDto, jsonFil)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("<reiseutgifter"))
		assertTrue(xmlString.contains("<reisestoenadForArbeidssoeker"))

	}

	@Test
	fun json2XmlTest_flytting() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.23B", tema = "TSR").build()
		val jsonFil = Hjelpemetoder.getBytesFromFile("/__files/tilleggsstonad-NAV-11-12.23B-27022024.json")

		val xmlFil = json2Xml(soknadDto, jsonFil)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("<flytteutgifter"))
	}

	@Test
	fun json2XmlTest_bolig_overnatting() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.19B", tema = "TSO").build()
		val jsonFil = Hjelpemetoder.getBytesFromFile("/__files/bolig-overnatting-NAV-11-12.19B-19032024.json")

		val xmlFil = json2Xml(soknadDto, jsonFil)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("<boutgifter"))
	}

	@Test
	fun json2XmlTest_barnepass() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.15B", tema = "TSO").build()
		val jsonFil = Hjelpemetoder.getBytesFromFile("/__files/barnepass-NAV-11-12.15B.json")

		val xmlFil = json2Xml(soknadDto, jsonFil)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("<tilsynsutgifterBarn"))
		assertTrue(xmlString.contains("<barn>"))
	}

	@Test
	fun `Convert to XML of daily travel expences - using own car`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.21B", tema = "TSO").build()
		val dagligReise =
			JsonDagligReiseTestBuilder()
				.soknadsPeriode("2023-12-01", "2024-06-20")
				.hvorLangReiseveiHarDu(130)
				.velgLand1(VelgLand(label = "Norge", value = "NO"))
				.adresse1("Kongensgate 10")
				.postnr1("3701")
				.kanDuReiseKollektivtDagligReise("Nei")
				.hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt("helsemessigeArsaker")
				.kanBenytteEgenBil(
					KanBenytteEgenBil(
						bompenger = 150,
						piggdekkavgift = 1000,
						ferje = null,
						annet = null,
						vilDuHaUtgifterTilParkeringPaAktivitetsstedet = "JA",
						parkering = 200,
						hvorOfteOnskerDuASendeInnKjoreliste = "jegOnskerALevereKjorelisteEnGangIUken"
					)
				)
				.kanIkkeBenytteEgenBil(kanIkkeBenytteEgenBil = null)
				.build()
		val jsonReisestottesoknad = JsonReiseTestBuilder().dagligReise(dagligReise = dagligReise).build()
		val tilleggsstonad =
			JsonApplicationTestBuilder().rettighetstyper(rettighetstype = jsonReisestottesoknad).build()

		val xmlFil = json2Xml(soknadDto, tilleggsstonad)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("<periode>"))
		assertTrue(xmlString.contains("<fom>2023-12-01+01:00</fom>")) //##
		assertTrue(xmlString.contains("<tom>2024-06-20+02:00</tom>"))
		assertTrue(xmlString.contains("</periode>"))
		assertTrue(xmlString.contains("<aktivitetsadresse>Kongensgate 10, 3701</aktivitetsadresse>"))
		assertTrue(xmlString.contains("<dagligReise>"))
		assertTrue(xmlString.contains("<avstand>130.0</avstand>"))
		assertTrue(xmlString.contains("<kanOffentligTransportBrukes>false</kanOffentligTransportBrukes>"))
		assertTrue(xmlString.contains("<aarsakTilIkkeOffentligTransport>helsemessigeArsaker</aarsakTilIkkeOffentligTransport>"))
		assertTrue(xmlString.contains("<innsendingsintervall>uke</innsendingsintervall>"))
		assertTrue(xmlString.contains("<sumAndreUtgifter>1150.0</sumAndreUtgifter>"))
		assertTrue(xmlString.contains("<parkeringsutgiftBeloep>200</parkeringsutgiftBeloep>"))
	}

	@Test
	fun `Convert to XML of daily travel excpenses - using public transport`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.21B", tema = "TSO").build()
		val dagligReise = JsonDagligReiseTestBuilder()
			.velgLand1(VelgLand(label = "Sverige", value = "SE"))
			.adresse1("Strømstad Systembolag, 452 38")
			.postnr1(null)
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
		assertTrue(xmlString.contains("<aktivitetsadresse>Strømstad Systembolag, 452 38, Sverige</aktivitetsadresse>"))
		assertTrue(xmlString.contains("<kanOffentligTransportBrukes>true</kanOffentligTransportBrukes>"))
		assertTrue(xmlString.contains("<beloepPerMaaned>2000</beloepPerMaaned>"))
	}


	@Test
	fun `Convert to XML of daily travel excpenses - using taxi`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.21B", tema = "TSO").build()
		val dagligReise = JsonDagligReiseTestBuilder()
			.soknadsPeriode("2024-01-02", "2024-06-18")
			.hvorLangReiseveiHarDu(10)
			.kanDuReiseKollektivtDagligReise("Nei")
			.hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt("hentingEllerLeveringAvBarn")
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
		assertTrue(xmlString.contains("<aarsakTilIkkeOffentligTransport>hentingEllerLeveringAvBarn</aarsakTilIkkeOffentligTransport>"))
		assertTrue(xmlString.contains("<aarsakTilIkkeEgenBil>disponererIkkeBil</aarsakTilIkkeEgenBil>"))
		assertTrue(
			xmlString.contains(
				"          <drosjeTransportutgifter>\n" +
					"            <beloep>6000</beloep>\n" +
					"          </drosjeTransportutgifter>\n"
			)
		)
	}


	@Test
	fun `Convert to XML of daily travel excpenses - neither own car nor taxi`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.21B", tema = "TSR").build()
		val dagligReise = JsonDagligReiseTestBuilder()
			.soknadsPeriode("2024-01-02", "2024-06-18")
			.hvorLangReiseveiHarDu(10)
			.kanDuReiseKollektivtDagligReise("Nei")
			.hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt("hentingEllerLeveringAvBarn")
			.kanBenytteEgenBil(null)
			.kanDuBenytteDrosje("Ja")
			.oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIPeriodenDuSokerOmStonadFor(6000)
			.kanIkkeBenytteEgenBil(
				KanIkkeBenytteEgenBil(
					hvaErArsakenTilAtDuIkkeKanBenytteEgenBil = "disponererIkkeBil",
					hvilkeAndreArsakerGjorAtDuIkkeKanBenytteEgenBil = null,
					kanDuBenytteDrosje = "Nei",
					oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIperiodenDuSokerOmStonadFor = null,
					hvorforKanDuIkkeBenytteDrosje = "Er sengeliggende og trenger transport med ambulanse"
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
		assertTrue(xmlString.contains("<aarsakTilIkkeOffentligTransport>hentingEllerLeveringAvBarn</aarsakTilIkkeOffentligTransport>"))
		assertTrue(xmlString.contains("<aarsakTilIkkeEgenBil>disponererIkkeBil</aarsakTilIkkeEgenBil>"))
		assertTrue(xmlString.contains("<aarsakTilIkkeDrosje>Er sengeliggende og trenger transport med ambulanse</aarsakTilIkkeDrosje>"))
	}

	@Test
	fun `Default case test convert to XML of meeting conventions travel excpenses`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.17B", tema = "TSO").build()
		val reiseSamling =
			JsonReiseSamlingTestBuilder()
				.startOgSluttdatoForSamlingene(
					startOgSluttdatoForSamlingene = listOf(
						JsonPeriode(startdatoDdMmAaaa = "2024-01-02", sluttdatoDdMmAaaa = "2024-01-07"),
						JsonPeriode(startdatoDdMmAaaa = "2024-02-02", sluttdatoDdMmAaaa = "2024-02-07")
					)
				)
				.hvorLangReiseveiHarDu1(120)
				.kanReiseKollektivt(KanReiseKollektivt(hvilkeUtgifterHarDuIForbindelseMedReisen1 = 1000))
				.build()
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
		assertTrue(xmlString.contains("<reiseObligatoriskSamling>"))
		assertTrue(xmlString.contains("<periode>"))
		assertTrue(xmlString.contains("<fom>2024-01-02+01:00</fom>")) //##
		assertTrue(xmlString.contains("<tom>2024-02-07+01:00</tom>"))
		assertTrue(xmlString.contains("</periode>"))
		assertTrue(xmlString.contains("</reiseObligatoriskSamling>"))
		assertTrue(xmlString.contains("<samlingsperiode>"))
		assertTrue(xmlString.contains("</samlingsperiode>"))
		assertTrue(xmlString.contains("kanOffentligTransportBrukes>true</kanOffentligTransportBrukes>"))
		assertTrue(xmlString.contains("<beloepPerMaaned>1000</beloepPerMaaned>"))

	}

	@Test
	fun `Default case test convert to XML of start and end of activity excpenses`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.18B", tema = "TSO").build()
		val oppstartOgAvslutningAvAktivitet =
			JsonReiseOppstartSluttTestBuilder()
				.soknadsPeriode(
					Date.formatToLocalDate(LocalDateTime.now().minusMonths(1)),
					Date.formatToLocalDate(LocalDateTime.now().plusMonths(3))
				)
				.hvorLangReiseveiHarDu2(100)
				.hvorMangeGangerSkalDuReiseEnVei(4)
				.harDuBarnSomSkalFlytteMedDeg("Ja")
				.harDuBarnSomBorHjemmeOgSomIkkeErFerdigMedFjerdeSkolear("Ja")
				.barnSomSkalFlytteMedDeg(
					listOf(
						BarnSomSkalFlytteMedDeg(
							fornavn = "Lite",
							etternavn = "Barn",
							fodselsdatoDdMmAaaa = "2020-03-03"
						)
					)
				)
				.hvilkeUtgifterHarDuIForbindelseMedReisen4(3000)
				.build()
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
		assertTrue(xmlString.contains("<kanOffentligTransportBrukes>true</kanOffentligTransportBrukes>"))
		assertTrue(xmlString.contains("<harBarnUnderFemteklasse>true</harBarnUnderFemteklasse>"))
		assertTrue(xmlString.contains("<antallReiser>4</antallReiser>"))
		assertTrue(xmlString.contains("<beloepPerMaaned>3000</beloepPerMaaned>"))

	}


	@Test
	fun `Case test convert to XML of start and end of activity excpenses - taxi expences `() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.18B", tema = "TSO").build()
		val oppstartOgAvslutningAvAktivitet =
			JsonReiseOppstartSluttTestBuilder()
				.soknadsPeriode(
					Date.formatToLocalDate(LocalDateTime.now().minusMonths(1)),
					Date.formatToLocalDate(LocalDateTime.now().plusMonths(3))
				)
				.hvorLangReiseveiHarDu2(100)
				.hvorMangeGangerSkalDuReiseEnVei(4)
				.kanDuReiseKollektivtOppstartAvslutningHjemreise("Nei")
				.kanIkkeReiseKollektivtOppstartAvslutningHjemreise(
					KanIkkeReiseKollektivt(
						hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt = "hentingEllerLeveringAvBarn",
						beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt = null,
						hentingEllerLeveringAvBarn = HentingEllerLeveringAvBarn(
							adressenHvorDuHenterEllerLevererBarn = "Damfaret 12", postnr = "0682"
						),
						annet  = null,
						kanDuBenytteEgenBil = "Nei",
						kanBenytteEgenBil = null,
						kanIkkeBenytteEgenBil = KanIkkeBenytteEgenBil(
							hvaErArsakenTilAtDuIkkeKanBenytteEgenBil = "eierIkkeBil",
							hvilkeAndreArsakerGjorAtDuIkkeKanBenytteEgenBil = null,
							kanDuBenytteDrosje = "Ja",
							oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIperiodenDuSokerOmStonadFor = 6000
						)
					)
				)
				.build()
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
		assertTrue(xmlString.contains("<beloep>6000</beloep>"))

	}

	@Test
	fun `Default case test convert to XML of job applying expences`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.22B", tema = "TSR").build()
		val reiseArbeidssoker =
			JsonReiseArbeidssokerTestBuilder()
				.reisedatoDdMmAaaa("2023-12-24")
				.hvorforReiserDuArbeidssoker("jobbintervju")
				.dekkerAndreEnnNavEllerDegSelvReisenHeltEllerDelvis("Nei")
				.mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene("Ja")
				.harMottattDagpengerSiste6Maneder(
					HarMottattDagpengerSiste6Maneder(
						harDuHattForlengetVentetidDeSisteAtteUkene = "Nei",
						harDuHattTidsbegrensetBortfallDeSisteAtteUkene = "Nei"
					)
				)
				.hvorLangReiseveiHarDu3(150)
				.kanDuReiseKollektivtArbeidssoker("Ja")
				.hvilkeUtgifterHarDuIForbindelseMedReisen3(5000)
				.build()
		val jsonReisestottesoknad = JsonReiseTestBuilder().reiseArbeidssoker(reiseArbeidssoker).build()
		val tilleggsstonad =
			JsonApplicationTestBuilder().rettighetstyper(rettighetstype = jsonReisestottesoknad).build()

		val xmlFil = json2Xml(
			soknadDto, tilleggsstonad
		)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("<reisestoenadForArbeidssoeker>"))
		assertTrue(xmlString.contains("<reisedato>2023-12-24+01:00</reisedato>")) //##
		assertTrue(xmlString.contains("<formaal>JOBB</formaal>"))
		assertTrue(xmlString.contains("<harMottattDagpengerSisteSeksMaaneder>true</harMottattDagpengerSisteSeksMaaneder>"))
		assertTrue(xmlString.contains("<avstand>150</avstand>"))
		assertTrue(xmlString.contains("<erUtgifterDekketAvAndre>false</erUtgifterDekketAvAndre>"))
		assertTrue(xmlString.contains("<erVentetidForlenget>false</erVentetidForlenget>"))
		assertTrue(xmlString.contains("<finnesTidsbegrensetbortfall>false</finnesTidsbegrensetbortfall>"))
		assertTrue(xmlString.contains("beloepPerMaaned>5000</beloepPerMaaned>"))

	}


	@Test
	fun `Default case test convert to XML child care expenses`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.15B", tema = "TSO").build()
		val fnrBarn = "01902399964"
		val fnrForeldreTo = "05844198215"
		val utgifter = 4000
		val barnePass = JsonBarnePassTestBuilder()
			.fradato("2023-12-01")
			.tildato("2024-06-20")
			.barnePass(
				barnePass = listOf(
					BarnePass(
						fornavn = "Lite",
						etternavn = "Barn",
						fodselsdatoDdMmAaaa = fnrBarn,
						jegSokerOmStonadTilPassAvDetteBarnet = true,
						sokerStonadForDetteBarnet = SokerStonadForDetteBarnet(
							hvemPasserBarnet = "barnehageEllerSfo",
							oppgiManedligUtgiftTilBarnepass = utgifter,
							harBarnetFullfortFjerdeSkolear = "Nei",
							hvaErArsakenTilAtBarnetDittTrengerPass = null
						)
					)
				)
			)
			.fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa(fnrForeldreTo)
			.fodselsnummerDNummerAndreForelder(null)
			.build()
		val tilleggsstonad =
			JsonApplicationTestBuilder().rettighetstyper(rettighetstype = barnePass).build()
		val xmlFil = json2Xml(soknadDto, tilleggsstonad)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()

		assertTrue(xmlString.contains("<tilleggsstoenadsskjema>"))
		assertTrue(xmlString.contains("<tilsynsutgifterBarn>"))
		assertTrue(xmlString.contains("<periode>"))
		assertTrue(xmlString.contains("<fom>2023-12-01+01:00</fom>")) //##
		assertTrue(xmlString.contains("<tom>2024-06-20+02:00</tom>"))
		assertTrue(xmlString.contains("</periode>"))
		assertTrue(xmlString.contains("<personidentifikator>$fnrBarn</personidentifikator>"))
		assertTrue(xmlString.contains("<tilsynskategori>OFF</tilsynskategori>"))
		assertTrue(xmlString.contains("<harFullfoertFjerdeSkoleaar>false</harFullfoertFjerdeSkoleaar>"))
		assertTrue(xmlString.contains("<maanedligUtgiftTilsynBarn>$utgifter</maanedligUtgiftTilsynBarn>"))
		assertTrue(xmlString.contains("annenForsoergerperson>$fnrForeldreTo</annenForsoergerperson>"))

	}

	@Test
	fun `Case test convert to XML child care expenses - two children`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.15B", tema = "TSO").build()
		val fnrBarn1 = "23922399883"
		val fnrBarn2 = "01902399964"
		val fnrForeldreTo = "05844198215"
		val utgifter1 = 5000
		val utgifter2 = 3500
		val barnePass = JsonBarnePassTestBuilder()
			.barnePass(
				listOf(
					BarnePass(
						fornavn = "Fnavn",
						etternavn = "Enavn",
						fodselsdatoDdMmAaaa = fnrBarn1,
						jegSokerOmStonadTilPassAvDetteBarnet = true,
						sokerStonadForDetteBarnet = SokerStonadForDetteBarnet(
							hvemPasserBarnet = "dagmammaEllerDagpappa",
							oppgiManedligUtgiftTilBarnepass = utgifter1,
							harBarnetFullfortFjerdeSkolear = "Nei",
							hvaErArsakenTilAtBarnetDittTrengerPass = null
						)
					),
					BarnePass(
						fornavn = "Fnavn2",
						etternavn = "Enavn",
						fodselsdatoDdMmAaaa = fnrBarn2,
						jegSokerOmStonadTilPassAvDetteBarnet = true,
						sokerStonadForDetteBarnet = SokerStonadForDetteBarnet(
							hvemPasserBarnet = "dagmammaEllerDagpappa",
							oppgiManedligUtgiftTilBarnepass = utgifter2,
							harBarnetFullfortFjerdeSkolear = "Ja",
							hvaErArsakenTilAtBarnetDittTrengerPass = "saerligBehovForPass"
						)
					)
				)
			)
			.fodselsnummerDNummerAndreForelder(fnrForeldreTo)
			.build()
		val tilleggsstonad =
			JsonApplicationTestBuilder().rettighetstyper(rettighetstype = barnePass).build()
		val xmlFil = json2Xml(soknadDto, tilleggsstonad)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("tilsynsutgifterBarn"))
		assertTrue(xmlString.contains("<barn>"))
		assertTrue(xmlString.contains("<personidentifikator>$fnrBarn1</personidentifikator>"))
		assertTrue(xmlString.contains("<tilsynskategori>KOM</tilsynskategori>"))
		assertTrue(xmlString.contains("<navn>Fnavn2 Enavn</navn>"))
		assertTrue(xmlString.contains("<harFullfoertFjerdeSkoleaar>false</harFullfoertFjerdeSkoleaar>"))
		assertTrue(xmlString.contains("<maanedligUtgiftTilsynBarn>$utgifter2</maanedligUtgiftTilsynBarn>"))
		assertTrue(xmlString.contains("</barn>"))
	}

	@Test
	fun `Default case test convert to XML housing expenses`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.19B", tema = "TSO").build()
		val utgifterHjemsted = 5000
		val utgifterAktivitetssted = 0
		val boStotte = JsonBostotteTestBuilder()
			.fradato("2023-12-02")
			.tildato("2024-06-20")
			.hvilkeBoutgifterSokerDuOmAFaDekket("fasteBoutgifter")
			.hvilkeAdresserHarDuBoutgifterPa(
				HvilkeAdresserHarDuBoutgifterPa(
					boutgifterPaAktivitetsadressen = false,
					boutgifterPaHjemstedet = true,
					boutgifterPaHjemstedetMittSomHarOpphortIForbindelseMedAktiviteten = false
				)
			)
			.boutgifterPaHjemstedetMitt(utgifterHjemsted)
			.boutgifterPaAktivitetsadressen(utgifterAktivitetssted)
			.erDetMedisinskeForholdSomPavirkerUtgifteneDinePaAktivitetsstedet("Nei")
			.mottarDuBostotteFraKommunen("Nei")
			.build()
		val tilleggsstonad =
			JsonApplicationTestBuilder().rettighetstyper(rettighetstype = boStotte).build()
		val xmlFil = json2Xml(
			soknadDto, tilleggsstonad
		)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("<boutgifter>"))
		assertTrue(xmlString.contains("<periode>"))
		assertTrue(xmlString.contains("<fom>2023-12-02+01:00</fom>")) //##
		assertTrue(xmlString.contains("<tom>2024-06-20+02:00</tom>"))
		assertTrue(xmlString.contains("</periode>"))
		assertTrue(xmlString.contains("<harBoutgifterVedSamling>false</harBoutgifterVedSamling>"))
		assertTrue(xmlString.contains("<harFasteBoutgifter>true</harFasteBoutgifter>"))
		assertTrue(xmlString.contains("<mottarBostoette>false</mottarBostoette>"))
		assertTrue(xmlString.contains("<boutgifterHjemstedAktuell>$utgifterHjemsted</boutgifterHjemstedAktuell>"))
		assertTrue(xmlString.contains("<boutgifterAktivitetsted>$utgifterAktivitetssted</boutgifterAktivitetsted>"))

	}

	@Test
	fun `Test convert to XML housing expenses - convention periods`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.19B", tema = "TSO").build()
		val utgifterHjemsted = 10000
		val utgifterAktivitetssted = 5000
		val utgifterOpphort = 4000
		val mottattStotte = 3500
		val boStotte = JsonBostotteTestBuilder()
			.fradato("2023-12-02")
			.tildato("2024-06-20")
			.hvilkeBoutgifterSokerDuOmAFaDekket("boutgifterIForbindelseMedSamling")
			.hvilkeAdresserHarDuBoutgifterPa(
				HvilkeAdresserHarDuBoutgifterPa(
					boutgifterPaAktivitetsadressen = true,
					boutgifterPaHjemstedet = true,
					boutgifterPaHjemstedetMittSomHarOpphortIForbindelseMedAktiviteten = true
				)
			)
			.mottarDuBostotteFraKommunen("Ja")
			.bostottebelop(mottattStotte)
			.bostotteIForbindelseMedSamling(
				BostotteIForbindelseMedSamling(
					periodeForSamling = listOf(
						JsonPeriode(startdatoDdMmAaaa = "2023-12-02", sluttdatoDdMmAaaa = "2023-12-20"),
						JsonPeriode(startdatoDdMmAaaa = "2024-01-02", sluttdatoDdMmAaaa = "2024-01-20"),
						JsonPeriode(startdatoDdMmAaaa = "2024-02-01", sluttdatoDdMmAaaa = "2024-02-20"),
						JsonPeriode(startdatoDdMmAaaa = "2024-06-01", sluttdatoDdMmAaaa = "2024-06-20")
					)
				)
			)
			.erDetMedisinskeForholdSomPavirkerUtgifteneDinePaAktivitetsstedet("Ja")
			.boutgifterPaAktivitetsadressen(utgifterAktivitetssted)
			.boutgifterPaHjemstedetMitt(utgifterHjemsted)
			.boutgifterJegHarHattPaHjemstedetMittMenSomHarOpphortIForbindelseMedAktiviteten(utgifterOpphort)
			.build()
		val tilleggsstonad =
			JsonApplicationTestBuilder().rettighetstyper(rettighetstype = boStotte).build()
		val xmlFil = json2Xml(
			soknadDto, tilleggsstonad
		)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("<boutgifter>"))
		assertTrue(xmlString.contains("<samlingsperiode>"))
		assertTrue(xmlString.contains("<fom>2023-12-02+01:00</fom>")) //##
		assertTrue(xmlString.contains("<tom>2023-12-20+01:00</tom>"))
		assertTrue(xmlString.contains("</samlingsperiode>"))
		assertTrue(xmlString.contains("<fom>2024-01-02+01:00</fom>"))
		assertTrue(xmlString.contains("<tom>2024-01-20+01:00</tom>"))
		assertTrue(xmlString.contains("<fom>2024-02-01+01:00</fom>"))
		assertTrue(xmlString.contains("<tom>2024-02-20+01:00</tom>"))
		assertTrue(xmlString.contains("<fom>2024-06-01+02:00</fom>"))
		assertTrue(xmlString.contains("<tom>2024-06-20+02:00</tom>"))
		assertTrue(xmlString.contains("<harBoutgifterVedSamling>true</harBoutgifterVedSamling>"))
		assertTrue(xmlString.contains("<harFasteBoutgifter>true</harFasteBoutgifter>"))
		assertTrue(xmlString.contains("<mottarBostoette>true</mottarBostoette>"))
		assertTrue(xmlString.contains("<bostoetteBeloep>$mottattStotte</bostoetteBeloep>"))
		assertTrue(xmlString.contains("<boutgifterHjemstedAktuell>$utgifterHjemsted</boutgifterHjemstedAktuell>"))
		assertTrue(xmlString.contains("<boutgifterAktivitetsted>$utgifterAktivitetssted</boutgifterAktivitetsted>"))
		assertTrue(xmlString.contains("<boutgifterHjemstedOpphoert>$utgifterOpphort</boutgifterHjemstedOpphoert>"))
	}

	@Test
	fun `Default case test convert to XML learning material expenses`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.16B", tema = "TSO").build()
		val laerestotte = JsonLaeremiddelTestBuilder()
			.fradato("2023-12-02")
			.tildato("2024-06-20")
			.farDuDekketLaeremidlerEtterAndreOrdninger("Delvis")
			.hvorMyeFarDuDekketAvEnAnnenAktor(1500)
			.hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore("videregaendeUtdanning")
			.hvilketKursEllerAnnenFormForUtdanningSkalDuTa(null)
			.oppgiHvorMangeProsentDuStudererEllerGarPaKurs(100)
			.utgifterTilLaeremidler(7500)
			.hvorMyeFarDuDekketAvEnAnnenAktor(1500)
			.hvorStortBelopSokerDuOmAFaDekketAvNav(6000)
			.build()
		val tilleggsstonad =
			JsonApplicationTestBuilder().rettighetstyper(rettighetstype = laerestotte).build()
		val xmlFil = json2Xml(soknadDto, tilleggsstonad)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("laeremiddelutgifter"))
		assertTrue(xmlString.contains("<laeremiddelutgifter>"))
		assertTrue(xmlString.contains("<periode>"))
		assertTrue(xmlString.contains("<fom>2023-12-02+01:00</fom>")) //##
		assertTrue(xmlString.contains("<tom>2024-06-20+02:00</tom>"))
		assertTrue(xmlString.contains("</periode>"))
		assertTrue(xmlString.contains("<skolenivaa kodeverksRef=\"Skolenivaa\">VGS</skolenivaa>"))
		assertTrue(xmlString.contains("<prosentandelForUtdanning>100</prosentandelForUtdanning>"))
		assertTrue(xmlString.contains("<erUtgifterDekket kodeverksRef=\"utgifterdekket\">DEL</erUtgifterDekket>"))
		assertTrue(xmlString.contains("<beloep>7500</beloep>"))
		assertTrue(xmlString.contains("<hvorMyeDekkesAvAnnenAktoer>1500.0</hvorMyeDekkesAvAnnenAktoer>"))
		assertTrue(xmlString.contains("<hvorMyeDekkesAvNAV>6000.0</hvorMyeDekkesAvNAV>"))
	}


	@Test
	fun `Convert to XML learning material expenses - Universitet deltid, ikke dekket  `() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.16B", tema = "TSO").build()
		val laerestotte = JsonLaeremiddelTestBuilder()
			.fradato("2023-12-02")
			.tildato("2024-06-20")
			.farDuDekketLaeremidlerEtterAndreOrdninger("Nei")
			.hvorMyeFarDuDekketAvEnAnnenAktor(null)
			.hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore("hoyereUtdanning")
			.hvilketKursEllerAnnenFormForUtdanningSkalDuTa(null)
			.oppgiHvorMangeProsentDuStudererEllerGarPaKurs(50)
			.utgifterTilLaeremidler(12000)
			.hvorMyeFarDuDekketAvEnAnnenAktor(null)
			.hvorStortBelopSokerDuOmAFaDekketAvNav(10000)
			.build()
		val tilleggsstonad =
			JsonApplicationTestBuilder().rettighetstyper(rettighetstype = laerestotte).build()
		val xmlFil = json2Xml(soknadDto, tilleggsstonad)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("<laeremiddelutgifter>"))
		assertTrue(xmlString.contains("<fom>2023-12-02+01:00</fom>"))
		assertTrue(xmlString.contains("<tom>2024-06-20+02:00</tom>"))
		assertTrue(xmlString.contains("<skolenivaa kodeverksRef=\"Skolenivaa\">HGU</skolenivaa>"))
		assertTrue(xmlString.contains("<prosentandelForUtdanning>50</prosentandelForUtdanning>"))
		assertTrue(xmlString.contains("<erUtgifterDekket kodeverksRef=\"utgifterdekket\">NEI</erUtgifterDekket>"))
		assertTrue(xmlString.contains("<beloep>12000</beloep>"))
		assertTrue(xmlString.contains("<hvorMyeDekkesAvNAV>10000.0</hvorMyeDekkesAvNAV>"))
	}


	@Test
	fun `Test convert to XML moving expenses - move by myself`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.16B", tema = "TSO").build()

		val flytteutgifter = JsonFlyttingTestBuilder()
			.hvorforFlytterDu("nyJobb")
			.oppgiForsteDagINyJobbDdMmAaaa("2024-01-02")
			.velgLand1(VelgLand(label = "Norge", value = "NO"))
			.adresse1("Kongens gate 10")
			.postnr1("3701")
			.farDuDekketUtgifteneDineTilFlyttingPaAnnenMateEnnMedStonadFraNav("Nei")
			.erBostedEtterFlytting(true)
			.narFlytterDuDdMmAaaa("2023-12-29")
			.ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra("jegFlytterSelv")
			.jegFlytterSelv(
				JegFlytterSelv(
					hvorLangtSkalDuFlytte = 130, hengerleie = 1000, bom = null, parkering = 200, ferje = 0, annet = null
				)
			)
			.build()
		val tilleggsstonad =
			JsonApplicationTestBuilder().rettighetstyper(rettighetstype = flytteutgifter).build()
		val xmlFil = json2Xml(soknadDto, tilleggsstonad)

		assertNotNull(xmlFil)
		val xmlString = xmlFil.decodeToString()
		assertTrue(xmlString.contains("flytteutgifter"))
		assertTrue(xmlString.contains("flyttingPgaAktivitet>false</flyttingPgaAktivitet>"))
		assertTrue(xmlString.contains("<erUtgifterTilFlyttingDekketAvAndreEnnNAV>false</erUtgifterTilFlyttingDekketAvAndreEnnNAV>"))
		assertTrue(xmlString.contains("<flyttingPgaNyStilling>true</flyttingPgaNyStilling>"))
		assertTrue(xmlString.contains("<flytterSelv>true</flytterSelv>"))
		assertTrue(xmlString.contains("<flyttedato>2023-12-29+01:00</flyttedato>")) //##
		assertTrue(xmlString.contains("<tiltredelsesdato>2024-01-02+01:00</tiltredelsesdato>"))
		assertTrue(xmlString.contains("<avstand>130</avstand>"))
		assertTrue(xmlString.contains("<tilflyttingsadresse>Kongens gate 10, 3701</tilflyttingsadresse>"))
		assertTrue(xmlString.contains("<sumTilleggsutgifter>1200.0</sumTilleggsutgifter>"))

	}

	@Test
	fun `Convert to XML moving expenses - Paying for transportation`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.23B", tema = "TSO").build()

		val flytteutgifter = JsonFlyttingTestBuilder()
			.ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra("jegVilBrukeFlyttebyra")
			.jegVilBrukeFlyttebyra(
				JegVilBrukeFlyttebyra(
					navnPaFlyttebyra1 = "Flytte1",
					belop = 4000,
					navnPaFlyttebyra2 = "Flytte2",
					belop1 = 5000,
					jegVelgerABruke = "Flytte1",
					hvorLangtSkalDuFlytte1 = 130
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
		assertTrue(xmlString.contains("<avstand>130</avstand>"))
		assertTrue(xmlString.contains("<valgtFlyttebyraa>Flytte1</valgtFlyttebyraa>"))

	}

	@Test
	fun `Convert to XML moving expenses - Chosen to move by myself after receiving offers`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.23B", tema = "TSO").build()

		val flytteutgifter = JsonFlyttingTestBuilder()
			.ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra("jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv")
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
		assertTrue(xmlString.contains("<avstand>130</avstand>"))

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
