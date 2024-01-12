package no.nav.soknad.innsending.util.mapping.tilleggsstonad

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import junit.framework.TestCase.assertEquals
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.tilleggsstonad.FyllUtJsonTestBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class TilleggsstonadJson2JsonConverterTest {

	@Test
	fun `happy case - mapping av reisesoknad til strukturert json`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()
		val aktivitetsId = "12345"
		val language = "no-Nb"
		val maalgruppeType = "NEDSARBEV"
		val fyllUtObj = FyllUtJsonTestBuilder()
			.aktivitetsId(aktivitetsId)
			.language(language)
			.maalgrupper(null)
			.arenaMaalgruppe(
				JsonMaalgruppeinformasjon(
					periode = null,
					kilde = "BRUKERDEFINERT",
					maalgruppetype = "NEDSARBEV"
				)
			)
			.build()

		val mapper = jacksonObjectMapper()
		val fyllUtJson = mapper.writeValueAsString(fyllUtObj)
		val strukturertJson = convertToJson(soknadDto, fyllUtJson.toString().toByteArray())

		assertTrue(strukturertJson != null)
		assertEquals(aktivitetsId, strukturertJson.tilleggsstonad.aktivitetsinformasjon?.aktivitet)
		assertEquals(maalgruppeType, strukturertJson.tilleggsstonad.maalgruppeinformasjon?.maalgruppetype)
	}

	@Test
	fun `Nedsattarbeidsevne - Mapping av brukers livssituasjon til prioritert maalgruppe`() {
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()
		val aktivitetsId = "12345"
		val maalgruppeType = "NEDSARBEVN"
		val fyllUtObj = FyllUtJsonTestBuilder()
			.aktivitetsId(aktivitetsId)
			.maalgrupper(null)
			.maalgrupper(
				mapOf(
					"harDuNedsattArbeidsevnePaGrunnAvSykdom" to "ja",
					"mottarDuSykepenger" to "nei",
					"harDuVedtakFraNavOmNedsattArbeidsevnePaGrunnAvSykdom" to "ja",
					//"mottarDuLonnFraArbeidsgiverMensDuGjennomforerEnAktivitetSomNavHarGodkjent" to "nei",
					"erDuArbeidssoker" to "ja",
					"mottarDuEllerHarDuSoktOmDagpenger" to "ja"
				)
			)
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
		val soknadDto = DokumentSoknadDtoTestBuilder(skjemanr = "NAV 11-12.12B", tema = "TSO").build()
		val aktivitetsId = "12345"
		val maalgruppeType = "MOTDAGPEN"
		val fyllUtObj = FyllUtJsonTestBuilder()
			.aktivitetsId(aktivitetsId)
			.maalgrupper(null)
			.maalgrupper(
				mapOf(
					"harDuNedsattArbeidsevnePaGrunnAvSykdom" to "ja",
					"mottarDuSykepenger" to "ja",
					"harDuVedtakFraNavOmNedsattArbeidsevnePaGrunnAvSykdom" to "ja",
					//"mottarDuLonnFraArbeidsgiverMensDuGjennomforerEnAktivitetSomNavHarGodkjent" to "nei",
					"erDuArbeidssoker" to "ja",
					"mottarDuEllerHarDuSoktOmDagpenger" to "ja"
				)
			)
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
			.maalgrupper(null)
			.maalgrupper(
				mapOf(
					"erDuUgiftSkiltEllerSeparertOgErAleneOmOmsorgenForBarn1" to "ja",
					"gjennomforerDuEnUtdanningSomNavHarGodkjent" to "ja",
					"erDuArbeidssoker" to "ja",
				)
			)
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
			.maalgrupper(null)
			.maalgrupper(
				mapOf(
					"erDuTidligereFamiliepleier" to "ja",
					"gjennomforerDuEnUtdanningSomNavHarGodkjent" to "ja",
					"erDuGjenlevendeEktefelle" to "ja",
					"erDuArbeidssoker" to "ja",
				)
			)
			.build()

		val mapper = jacksonObjectMapper()
		val fyllUtJson = mapper.writeValueAsString(fyllUtObj)
		val strukturertJson = convertToJson(soknadDto, fyllUtJson.toString().toByteArray())

		assertTrue(strukturertJson != null)
		assertEquals(aktivitetsId, strukturertJson.tilleggsstonad.aktivitetsinformasjon?.aktivitet)
		assertEquals(maalgruppeType, strukturertJson.tilleggsstonad.maalgruppeinformasjon?.maalgruppetype)
	}

}
