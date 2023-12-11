package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.util.mapping.*

class JsonReiseSamlingTestBuilder(
	var startOgSluttdatoForSamlingene: List<StartOgSluttdatoForSamlingene> = listOf(
		StartOgSluttdatoForSamlingene(
			startdatoDdMmAaaa = "2024-01-02",
			sluttdatoDdMmAaaa = "2024-01-07"
		), StartOgSluttdatoForSamlingene(startdatoDdMmAaaa = "2024-02-02", sluttdatoDdMmAaaa = "2024-02-07")
	),
	var hvorLangReiseveiHarDu1: Int = 120,
	var velgLandReiseTilSamling: VelgLandReiseTilSamling = VelgLandReiseTilSamling(label = "NO", value = "Norge"),
	var adresse2: String = "Kongensgate 10",
	var postnr2: String = "3701",
	var kanDuReiseKollektivtReiseTilSamling: String = "Ja",
	var kanReiseKollektivt: KanReiseKollektivt? = KanReiseKollektivt(hvilkeUtgifterHarDuIForbindelseMedReisen1 = 1000),
	var kanIkkeReiseKollektivtReiseTilSamling: KanIkkeReiseKollektivtReiseTilSamling? = null,
	var bekreftelseForAlleSamlingeneDuSkalDeltaPa: String = "dfasdf"
) {

	fun build() = JsonReiseSamling(
		startOgSluttdatoForSamlingene = startOgSluttdatoForSamlingene,
		hvorLangReiseveiHarDu1 = hvorLangReiseveiHarDu1,
		velgLandReiseTilSamling = velgLandReiseTilSamling,
		adresse2 = adresse2,
		postnr2 = postnr2,
		kanDuReiseKollektivtReiseTilSamling = kanDuReiseKollektivtReiseTilSamling,
		kanReiseKollektivt = kanReiseKollektivt,
		kanIkkeReiseKollektivtReiseTilSamling = kanIkkeReiseKollektivtReiseTilSamling,
		bekreftelseForAlleSamlingeneDuSkalDeltaPa = bekreftelseForAlleSamlingeneDuSkalDeltaPa
	)

}
