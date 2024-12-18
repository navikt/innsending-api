package no.nav.soknad.innsending.util.mapping.tilleggsstonad

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.soknad.innsending.model.MaalgruppeType
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.tilleggsstonad.FyllUtJsonTestBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class TilleggsstonadJson2JsonConverterTest {
	@Test
	fun `happy case - mapping av barnepass til strukturert json`() {
		val skjemanr = stotteTilPassAvBarn
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = skjemanr, tema = "TSO").build()
		val aktivitetsId = "12345"
		val language = "no-Nb"
		val forelderTo = "1990-01-26"
		val fnrForeldreTo = "16905198584"
		val fnrLiteBarn = "23922399883"
		val passAvBarn = listOf(
			OpplysningerOmBarn(
				fornavn = "Lite",
				etternavn = "Barn",
				fodselsdatoDdMmAaaa = "2019-03-07",
				fodselsnummerDNummer = fnrLiteBarn,
				jegSokerOmStonadTilPassAvDetteBarnet = true,
				sokerStonadForDetteBarnet = SokerStonadForDetteBarnet(
					hvemPasserBarnet = "barnehageEllerSfo",
					oppgiManedligUtgiftTilBarnepass = 6000.0,
					harBarnetFullfortFjerdeSkolear = "nei",
					hvaErArsakenTilAtBarnetDittTrengerPass = null
				)
			)
		)

		val maalgruppeType = MaalgruppeType.NEDSARBEVN
		val fyllUtObj = FyllUtJsonTestBuilder()
			.language(language)
			.skjemanr(skjemanr)
			.arenaAktivitetOgMaalgruppe(
				maalgruppe = maalgruppeType, aktivitetId = aktivitetsId, SkjemaPeriode("2024-01-02", "2024-03-30")
			)
			.periode("01-01-2024", "29-03-2024")
			.passAvBarn(passAvBarn)
			.fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa(forelderTo)
			.fodselsnummerDNummerAndreForelder(fnrForeldreTo)
			.build()


		val mapper = jacksonObjectMapper().findAndRegisterModules()

		val fyllUtJson = mapper.writeValueAsString(fyllUtObj)
		val strukturertJson =
			convertToJsonTilleggsstonad(soknadDto, fyllUtJson.toString().toByteArray())

		assertTrue(strukturertJson != null)
		Assertions.assertEquals(aktivitetsId, strukturertJson.applicationDetails.aktivitetsinformasjon?.aktivitet)
		Assertions.assertEquals(
			maalgruppeType.value,
			strukturertJson.applicationDetails.maalgruppeinformasjon?.maalgruppetype
		)
		Assertions.assertEquals(
			fnrLiteBarn,
			strukturertJson.applicationDetails.rettighetstype?.tilsynsutgifter?.barnePass?.first()?.fodselsdatoDdMmAaaa
		)
		Assertions.assertEquals(
			fnrForeldreTo,
			strukturertJson.applicationDetails.rettighetstype?.tilsynsutgifter?.fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa
		)
		Assertions.assertEquals(
			passAvBarn.size,
			strukturertJson.applicationDetails.rettighetstype?.tilsynsutgifter?.barnePass?.size
		)
	}

	@Test
	fun `happy case - mapping av laermidler til strukturert json`() {
		val skjemanr = stotteTilLaeremidler
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = skjemanr, tema = "TSO").build()
		val aktivitetsId = "12345"
		val language = "no-Nb"

		val maalgruppeType = MaalgruppeType.NEDSARBEVN
		val fyllUtObj = FyllUtJsonTestBuilder()
			.language(language)
			.skjemanr(skjemanr)
			.arenaAktivitetOgMaalgruppe(
				maalgruppe = maalgruppeType,
				aktivitetId = aktivitetsId,
				SkjemaPeriode("2024-01-02", "2024-03-30")
			)
			.periode("01-01-2024", "29-03-2024")
			.laeremidler(typeUtdanning = "videregaendeUtdanning", utgifter = 10000.0)
			.build()

		val mapper = jacksonObjectMapper().findAndRegisterModules()
		val fyllUtJson = mapper.writeValueAsString(fyllUtObj)
		val strukturertJson =
			convertToJsonTilleggsstonad(soknadDto, fyllUtJson.toString().toByteArray())

		assertTrue(strukturertJson != null)
		Assertions.assertEquals(aktivitetsId, strukturertJson.applicationDetails.aktivitetsinformasjon?.aktivitet)
		Assertions.assertEquals(
			maalgruppeType.value,
			strukturertJson.applicationDetails.maalgruppeinformasjon?.maalgruppetype
		)
		Assertions.assertEquals(
			"videregaendeUtdanning",
			strukturertJson.applicationDetails.rettighetstype?.laeremiddelutgifter?.hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore
		)
		Assertions.assertEquals(
			10000.0,
			strukturertJson.applicationDetails.rettighetstype?.laeremiddelutgifter?.utgifterTilLaeremidler
		)
	}


	@Test
	fun `happy case - mapping av reisesoknad til strukturert json`() {
		val skjemanr = reiseDaglig
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = skjemanr, tema = "TSO").build()
		val aktivitetsId = "12345"
		val language = "no-Nb"
		val maalgruppeType = MaalgruppeType.NEDSARBEVN
		val fyllUtObj = FyllUtJsonTestBuilder()
			.language(language)
			.skjemanr(skjemanr)
			.arenaAktivitetOgMaalgruppe(
				maalgruppe = maalgruppeType,
				aktivitetId = aktivitetsId,
				SkjemaPeriode("2024-01-02", "2024-03-30")
			)
			.periode("2024-01-01", "2024-03-29")
			.reisemal(VelgLand(label = "Norge", value = "NO"), adresse = "Kongensgate 10", postr = "3701", poststed = "Skien")
			.reiseAvstandOgFrekvens(hvorLangReiseveiHarDu = 120.0, hvorMangeReisedagerHarDuPerUke = 5.0)
			.reiseEgenBil(
				kanBenytteEgenBil = KanBenytteEgenBil(
					bompenger = 200.0,
					piggdekkavgift = 1000.0,
					ferje = 100.0,
					annet = 0.0,
					vilDuHaUtgifterTilParkeringPaAktivitetsstedet = "ja",
					parkering = 150.0,
					hvorOfteOnskerDuASendeInnKjoreliste = "jegOnskerALevereKjorelisteEnGangIManeden"
				)
			)
			.build()

		val mapper = jacksonObjectMapper().findAndRegisterModules()
		val fyllUtJson = mapper.writeValueAsString(fyllUtObj)
		val strukturertJson =
			convertToJsonTilleggsstonad(soknadDto, fyllUtJson.toString().toByteArray())

		assertTrue(strukturertJson != null)
		Assertions.assertEquals(aktivitetsId, strukturertJson.applicationDetails.aktivitetsinformasjon?.aktivitet)
		Assertions.assertEquals(
			maalgruppeType.value,
			strukturertJson.applicationDetails.maalgruppeinformasjon?.maalgruppetype
		)
		Assertions.assertEquals(
			"2024-01-01",
			strukturertJson.applicationDetails.rettighetstype?.reise?.dagligReise?.startdatoDdMmAaaa
		)
		Assertions.assertEquals(
			"Norge",
			strukturertJson.applicationDetails.rettighetstype?.reise?.dagligReise?.velgLand1?.label
		)
		Assertions.assertEquals(
			"Skien",
			strukturertJson.applicationDetails.rettighetstype?.reise?.dagligReise?.poststed
		)
		Assertions.assertEquals(
			120.0,
			strukturertJson.applicationDetails.rettighetstype?.reise?.dagligReise?.hvorLangReiseveiHarDu
		)
		Assertions.assertEquals(
			200.0,
			strukturertJson.applicationDetails.rettighetstype?.reise?.dagligReise?.kanIkkeReiseKollektivtDagligReise?.kanBenytteEgenBil?.bompenger
		)

	}


	@Test
	fun `medical reason - mapping av reisesoknad til strukturert json`() {
		val skjemanr = reiseDaglig
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = skjemanr, tema = "TSO").build()
		val aktivitetsId = "12345"
		val language = "no-Nb"
		val maalgruppeType = MaalgruppeType.NEDSARBEVN
		val fyllUtObj = FyllUtJsonTestBuilder()
			.language(language)
			.skjemanr(skjemanr)
			.arenaAktivitetOgMaalgruppe(
				maalgruppe = maalgruppeType,
				aktivitetId = aktivitetsId,
				SkjemaPeriode("2024-01-02", "2024-03-30")
			)
			.periode("2024-01-01", "2024-03-29")
			.reisemal(VelgLand(label = "Norge", value = "NO"), adresse = "Kongensgate 10", postr = "3701")
			.reiseAvstandOgFrekvens(hvorLangReiseveiHarDu = 5.0, hvorMangeReisedagerHarDuPerUke = 5.0)
			.harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde("ja")
			.reiseEgenBil(
				kanBenytteEgenBil = KanBenytteEgenBil(
					bompenger = 200.0,
					piggdekkavgift = 1000.0,
					ferje = 100.0,
					annet = 0.0,
					vilDuHaUtgifterTilParkeringPaAktivitetsstedet = "ja",
					parkering = 150.0,
					hvorOfteOnskerDuASendeInnKjoreliste = "jegOnskerALevereKjorelisteEnGangIManeden"
				)
			)
			.build()

		val mapper = jacksonObjectMapper().findAndRegisterModules()
		val fyllUtJson = mapper.writeValueAsString(fyllUtObj)
		val strukturertJson =
			convertToJsonTilleggsstonad(soknadDto, fyllUtJson.toString().toByteArray())

		assertTrue(strukturertJson != null)
		Assertions.assertEquals(aktivitetsId, strukturertJson.applicationDetails.aktivitetsinformasjon?.aktivitet)
		Assertions.assertEquals(
			maalgruppeType.value,
			strukturertJson.applicationDetails.maalgruppeinformasjon?.maalgruppetype
		)
		Assertions.assertEquals(
			"2024-01-01",
			strukturertJson.applicationDetails.rettighetstype?.reise?.dagligReise?.startdatoDdMmAaaa
		)
		Assertions.assertEquals(
			"Norge",
			strukturertJson.applicationDetails.rettighetstype?.reise?.dagligReise?.velgLand1?.label
		)
		Assertions.assertEquals(
			5.0,
			strukturertJson.applicationDetails.rettighetstype?.reise?.dagligReise?.hvorLangReiseveiHarDu
		)
		Assertions.assertEquals(
			200.0,
			strukturertJson.applicationDetails.rettighetstype?.reise?.dagligReise?.kanIkkeReiseKollektivtDagligReise?.kanBenytteEgenBil?.bompenger
		)
		Assertions.assertEquals(
			"ja",
			strukturertJson.applicationDetails.rettighetstype?.reise?.dagligReise?.harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde
		)

	}

}
