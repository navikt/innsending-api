package no.nav.soknad.innsending.utils.builders.tilleggsstonad

import no.nav.soknad.innsending.util.mapping.tilleggsstonad.*

class JsonReiseSamlingTestBuilder {
	private var startOgSluttdatoForSamlingene: List<JsonPeriode> = listOf(
		JsonPeriode(startdatoDdMmAaaa = "2024-01-02", sluttdatoDdMmAaaa = "2024-01-07"),
		JsonPeriode(startdatoDdMmAaaa = "2024-02-02", sluttdatoDdMmAaaa = "2024-02-07")
	)
	private var hvorLangReiseveiHarDu1: Int = 120
	private var velgLandReiseTilSamling: VelgLand = VelgLand(label = "Norge", value = "NO")
	private var adresse2: String = "Kongensgate 10"
	private var postnr2: String = "3701"
	private var kanDuReiseKollektivtReiseTilSamling: String = "Ja"
	private var kanReiseKollektivt: KanReiseKollektivt? =
		KanReiseKollektivt(hvilkeUtgifterHarDuIForbindelseMedReisen1 = 1000)
	private var kanIkkeReiseKollektivtReiseTilSamling: KanIkkeReiseKollektivt? = null
	private var bekreftelseForAlleSamlingeneDuSkalDeltaPa: String = "dfasdf"

	fun startOgSluttdatoForSamlingene(startOgSluttdatoForSamlingene: List<JsonPeriode>) =
		apply { this.startOgSluttdatoForSamlingene = startOgSluttdatoForSamlingene }

	fun hvorLangReiseveiHarDu1(hvorLangReiseveiHarDu1: Int) =
		apply { this.hvorLangReiseveiHarDu1 = hvorLangReiseveiHarDu1 }

	fun velgLandReiseTilSamling(velgLandReiseTilSamling: VelgLand) =
		apply { this.velgLandReiseTilSamling = velgLandReiseTilSamling }

	fun adresse2(adresse2: String) = apply { this.adresse2 = adresse2 }
	fun postnr2(postnr2: String) = apply { this.postnr2 = postnr2 }
	fun kanDuReiseKollektivtReiseTilSamling(kanDuReiseKollektivtReiseTilSamling: String) =
		apply { this.kanDuReiseKollektivtReiseTilSamling = kanDuReiseKollektivtReiseTilSamling }

	fun kanReiseKollektivt(kanReiseKollektivt: KanReiseKollektivt) =
		apply { this.kanReiseKollektivt = kanReiseKollektivt }

	fun kanIkkeReiseKollektivtReiseTilSamling(kanIkkeReiseKollektivtReiseTilSamling: KanIkkeReiseKollektivt) =
		apply { this.kanIkkeReiseKollektivtReiseTilSamling = kanIkkeReiseKollektivtReiseTilSamling }

	fun bekreftelseForAlleSamlingeneDuSkalDeltaPa(bekreftelseForAlleSamlingeneDuSkalDeltaPa: String) =
		apply { this.bekreftelseForAlleSamlingeneDuSkalDeltaPa = bekreftelseForAlleSamlingeneDuSkalDeltaPa }


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
