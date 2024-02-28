package no.nav.soknad.innsending.util.mapping.tilleggsstonad

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import junit.framework.TestCase.assertEquals
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.tilleggsstonad.FyllUtJsonTestBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class TilleggsstonadJson2JsonConverterTest {

	@Test
	fun `happy case - mapping av barnepass til strukturert json`() {
		val skjemanr = FyllUtJsonTestBuilder().barnepassSkjemanr
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = skjemanr, tema = "TSO").build()
		val aktivitetsId = "12345"
		val language = "no-Nb"
		val forelderTo = "10-10-1990"
		val passAvBarn = listOf(
			OpplysningerOmBarn(
				fornavn = "Lite",
				etternavn = "Barn",
				fodselsdatoDdMmAaaa = "2019-03-07",
				jegSokerOmStonadTilPassAvDetteBarnet = "ja",
				sokerStonadForDetteBarnet = SokerStonadForDetteBarnet(
					hvemPasserBarnet = "barnehageEllerSfo",
					oppgiManedligUtgiftTilBarnepass = 6000,
					harBarnetFullfortFjerdeSkolear = "nei",
					hvaErArsakenTilAtBarnetDittTrengerPass = null
				)
			)
		)

		val maalgruppeType = "NEDSARBEV"
		val fyllUtObj = FyllUtJsonTestBuilder()
			.aktivitetsId(aktivitetsId)
			.language(language)
			.skjemanr(skjemanr)
			.flervalg(null)
			.arenaMaalgruppe(
				JsonMaalgruppeinformasjon(
					periode = null,
					kilde = "BRUKERDEFINERT",
					maalgruppetype = "NEDSARBEV"
				)
			)
			.periode("01-01-2024", "29-03-2024")
			.passAvBarn(passAvBarn)
			.fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa(forelderTo)
			.build()

		val mapper = jacksonObjectMapper()
		val fyllUtJson = mapper.writeValueAsString(fyllUtObj)
		val strukturertJson = convertToJson(soknadDto, fyllUtJson.toString().toByteArray())

		assertTrue(strukturertJson != null)
		assertEquals(aktivitetsId, strukturertJson.tilleggsstonad.aktivitetsinformasjon?.aktivitet)
		assertEquals(maalgruppeType, strukturertJson.tilleggsstonad.maalgruppeinformasjon?.maalgruppetype)
		assertEquals(
			forelderTo,
			strukturertJson.tilleggsstonad.rettighetstype?.tilsynsutgifter?.fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa
		)
		assertEquals(passAvBarn.size, strukturertJson.tilleggsstonad.rettighetstype?.tilsynsutgifter?.barnePass?.size)
	}

	@Test
	fun `happy case - mapping av laermidler til strukturert json`() {
		val skjemanr = FyllUtJsonTestBuilder().laeremidlerSkjemanr
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = skjemanr, tema = "TSO").build()
		val aktivitetsId = "12345"
		val language = "no-Nb"

		val maalgruppeType = "NEDSARBEV"
		val fyllUtObj = FyllUtJsonTestBuilder()
			.aktivitetsId(aktivitetsId)
			.language(language)
			.skjemanr(skjemanr)
			.flervalg(null)
			.arenaMaalgruppe(
				JsonMaalgruppeinformasjon(
					periode = null,
					kilde = "BRUKERDEFINERT",
					maalgruppetype = "NEDSARBEV"
				)
			)
			.periode("01-01-2024", "29-03-2024")
			.laeremidler(typeUtdanning = "videregaendeUtdanning", utgifter = 10000)
			.build()

		val mapper = jacksonObjectMapper()
		val fyllUtJson = mapper.writeValueAsString(fyllUtObj)
		val strukturertJson = convertToJson(soknadDto, fyllUtJson.toString().toByteArray())

		assertTrue(strukturertJson != null)
		assertEquals(aktivitetsId, strukturertJson.tilleggsstonad.aktivitetsinformasjon?.aktivitet)
		assertEquals(maalgruppeType, strukturertJson.tilleggsstonad.maalgruppeinformasjon?.maalgruppetype)
		assertEquals(
			"videregaendeUtdanning",
			strukturertJson.tilleggsstonad.rettighetstype?.laeremiddelutgifter?.hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore
		)
		assertEquals(10000, strukturertJson.tilleggsstonad.rettighetstype?.laeremiddelutgifter?.utgifterTilLaeremidler)
	}


	@Test
	fun `happy case - mapping av reisesoknad til strukturert json`() {
		val skjemanr = FyllUtJsonTestBuilder().dagligReiseSkjemanr
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = skjemanr, tema = "TSO").build()
		val aktivitetsId = "12345"
		val language = "no-Nb"
		val maalgruppeType = "NEDSARBEV"
		val fyllUtObj = FyllUtJsonTestBuilder()
			.aktivitetsId(aktivitetsId)
			.language(language)
			.skjemanr(skjemanr)
			.flervalg(null)
			.arenaMaalgruppe(
				JsonMaalgruppeinformasjon(
					periode = null,
					kilde = "BRUKERDEFINERT",
					maalgruppetype = "NEDSARBEV"
				)
			)
			.periode("01-01-2024", "29-03-2024")
			.reisemal(VelgLand(label = "Norge", value = "NO"), adresse = "Kongensgate 10", postr = "3701")
			.reiseAvstandOgFrekvens(hvorLangReiseveiHarDu = 120, hvorMangeReisedagerHarDuPerUke = 5)
			.reiseEgenBil(
				kanBenytteEgenBil = KanBenytteEgenBil(
					bompenger = 200,
					piggdekkavgift = 1000,
					ferje = 100,
					annet = 0,
					vilDuHaUtgifterTilParkeringPaAktivitetsstedet = "ja",
					oppgiForventetBelopTilParkeringPaAktivitetsstedet = 150,
					hvorOfteOnskerDuASendeInnKjoreliste = "jegOnskerALevereKjorelisteEnGangIManeden"
				)
			)
			.build()

		val mapper = jacksonObjectMapper()
		val fyllUtJson = mapper.writeValueAsString(fyllUtObj)
		val strukturertJson = convertToJson(soknadDto, fyllUtJson.toString().toByteArray())

		assertTrue(strukturertJson != null)
		assertEquals(aktivitetsId, strukturertJson.tilleggsstonad.aktivitetsinformasjon?.aktivitet)
		assertEquals(maalgruppeType, strukturertJson.tilleggsstonad.maalgruppeinformasjon?.maalgruppetype)
		assertEquals("01-01-2024", strukturertJson.tilleggsstonad.rettighetstype?.reise?.dagligReise?.startdatoDdMmAaaa)
		assertEquals("Norge", strukturertJson.tilleggsstonad.rettighetstype?.reise?.dagligReise?.velgLand1?.label)
		assertEquals(120, strukturertJson.tilleggsstonad.rettighetstype?.reise?.dagligReise?.hvorLangReiseveiHarDu)
		assertEquals(
			200,
			strukturertJson.tilleggsstonad.rettighetstype?.reise?.dagligReise?.kanIkkeReiseKollektivtDagligReise?.kanBenytteEgenBil?.bompenger
		)

	}

	@Test
	fun `Nedsattarbeidsevne - Mapping av brukers livssituasjon til prioritert maalgruppe`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.21B", tema = "TSO").build()
		val aktivitetsId = "12345"
		val maalgruppeType = "NEDSARBEVN"
		val fyllUtObj = FyllUtJsonTestBuilder()
			.aktivitetsId(aktivitetsId)
			.flervalg(null)
			.flervalg(Flervalg(aapUforeNedsattArbEvne = true, regArbSoker = true, tiltakspenger = true))
			.build()

		val mapper = jacksonObjectMapper()
		val fyllUtJson = mapper.writeValueAsString(fyllUtObj)
		val strukturertJson = convertToJson(soknadDto, fyllUtJson.toString().toByteArray())

		assertTrue(strukturertJson != null)
		assertEquals(aktivitetsId, strukturertJson.tilleggsstonad.aktivitetsinformasjon?.aktivitet)
		assertEquals(maalgruppeType, strukturertJson.tilleggsstonad.maalgruppeinformasjon?.maalgruppetype)
	}

	@Test
	fun `Ikke Nedsattarbeidsevne når mottar sykepenger og dagpenger - Mapping av brukers livssituasjon til prioritert maalgruppe`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.21B", tema = "TSO").build()
		val aktivitetsId = "12345"
		val maalgruppeType = "MOTDAGPEN"
		val fyllUtObj = FyllUtJsonTestBuilder()
			.aktivitetsId(aktivitetsId)
			.flervalg(null)
			.flervalg(Flervalg(aapUforeNedsattArbEvne = false, dagpenger = true, regArbSoker = true))
			.build()

		val mapper = jacksonObjectMapper()
		val fyllUtJson = mapper.writeValueAsString(fyllUtObj)
		val strukturertJson = convertToJson(soknadDto, fyllUtJson.toString().toByteArray())

		assertTrue(strukturertJson != null)
		assertEquals(aktivitetsId, strukturertJson.tilleggsstonad.aktivitetsinformasjon?.aktivitet)
		assertEquals(maalgruppeType, strukturertJson.tilleggsstonad.maalgruppeinformasjon?.maalgruppetype)
	}

	@Test
	fun `Enslig forsørger under utdannelse - Mapping av brukers livssituasjon til prioritert maalgruppe`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()
		val aktivitetsId = "12345"
		val maalgruppeType = "ENSFORUTD"
		val fyllUtObj = FyllUtJsonTestBuilder()
			.aktivitetsId(aktivitetsId)
			.flervalg(null)
			.flervalg(Flervalg(ensligUtdanning = true, regArbSoker = true))
			.build()

		val mapper = jacksonObjectMapper()
		val fyllUtJson = mapper.writeValueAsString(fyllUtObj)
		val strukturertJson = convertToJson(soknadDto, fyllUtJson.toString().toByteArray())

		assertTrue(strukturertJson != null)
		assertEquals(aktivitetsId, strukturertJson.tilleggsstonad.aktivitetsinformasjon?.aktivitet)
		assertEquals(maalgruppeType, strukturertJson.tilleggsstonad.maalgruppeinformasjon?.maalgruppetype)
	}

	@Test
	fun `Tidligere familiepleier under utdannelse - Mapping av brukers livssituasjon til prioritert maalgruppe`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()
		val aktivitetsId = "12345"
		val maalgruppeType = "TIDLFAMPL"
		val fyllUtObj = FyllUtJsonTestBuilder()
			.aktivitetsId(aktivitetsId)
			.flervalg(null)
			.flervalg(Flervalg(tidligereFamiliepleier = true, regArbSoker = true, gjenlevendeUtdanning = true))
			.build()

		val mapper = jacksonObjectMapper()
		val fyllUtJson = mapper.writeValueAsString(fyllUtObj)
		val strukturertJson = convertToJson(soknadDto, fyllUtJson.toString().toByteArray())

		assertTrue(strukturertJson != null)
		assertEquals(aktivitetsId, strukturertJson.tilleggsstonad.aktivitetsinformasjon?.aktivitet)
		assertEquals(maalgruppeType, strukturertJson.tilleggsstonad.maalgruppeinformasjon?.maalgruppetype)
	}

}
