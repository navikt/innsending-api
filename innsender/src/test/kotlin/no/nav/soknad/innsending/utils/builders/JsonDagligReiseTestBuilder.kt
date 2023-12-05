package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.util.mapping.*

class JsonDagligReiseTestBuilder(
	var startdatoDdMmAaaa: String?,
	var sluttdatoDdMmAaaa: String?,
	var hvorMangeReisedagerHarDuPerUke: Int = 5,
	var harDuEnReiseveiPaSeksKilometerEllerMer: String = "Ja",
	var harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde: String? = null,
	var hvorLangReiseveiHarDu: Int = 10,
	var velgLand1: VelgLand1 = VelgLand1(
		label = "Norge", value = "NO"
	),
	var adresse1: String = "Kongensgate 10",
	var postnr1: String = "3701",
	var kanDuReiseKollektivtDagligReise: String = "Nei",
	var hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise: Int? = null,
	var beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt: String? = "Mange transportbytter, ekstra lang reisetid",
	var hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt: String? = "Ingen",
	var kanIkkeReiseKollektivtDagligReise: KanIkkeReiseKollektivtDagligReise? = KanIkkeReiseKollektivtDagligReise(
		hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt = "hentingEllerLeveringAvBarn",
		beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt = "Få og upraktiske tidspunkt for avganger",
		hentingEllerLeveringAvBarn = HentingEllerLeveringAvBarn(
			adressenHvorDuHenterEllerLevererBarn = "Damfaret 10",
			postnr = "0682"
		),
		annet = null,
		kanDuBenytteEgenBil = "ja",
		kanBenytteEgenBil = KanBenytteEgenBil(
			bompenger = 150,
			piggdekkavgift = 1000,
			ferje = null,
			annet = null,
			vilDuHaUtgifterTilParkeringPaAktivitetsstedet = "JA",
			oppgiForventetBelopTilParkeringPaAktivitetsstedet = 200,
			hvorOfteOnskerDuASendeInnKjoreliste = "UKE"
		),
		kanIkkeBenytteEgenBilDagligReise = null,
		kanDuBenytteDrosje = null,
		oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIPeriodenDuSokerOmStonadFor = null,
		hvorforKanDuIkkeBenytteDrosje = null
	)
) {
	fun build() = JsonDagligReise(
		startdatoDdMmAaaa = startdatoDdMmAaaa,
		sluttdatoDdMmAaaa = sluttdatoDdMmAaaa,
		hvorMangeReisedagerHarDuPerUke = hvorMangeReisedagerHarDuPerUke,
		harDuEnReiseveiPaSeksKilometerEllerMer = harDuEnReiseveiPaSeksKilometerEllerMer,
		harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde = harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde,
		hvorLangReiseveiHarDu = hvorLangReiseveiHarDu,
		velgLand1 = velgLand1,
		adresse1 = adresse1,
		postnr1 = postnr1,
		kanDuReiseKollektivtDagligReise = kanDuReiseKollektivtDagligReise,
		hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise = hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise,
		hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt = hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt,
		kanIkkeReiseKollektivtDagligReise = kanIkkeReiseKollektivtDagligReise
	)

}
