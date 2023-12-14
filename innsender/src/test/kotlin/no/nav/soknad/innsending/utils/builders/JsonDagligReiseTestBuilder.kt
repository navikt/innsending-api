package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.util.mapping.*
import no.nav.soknad.innsending.utils.Date
import java.time.LocalDateTime

class JsonDagligReiseTestBuilder {

	private var startdatoDdMmAaaa: String = Date.formatToLocalDate(LocalDateTime.now().minusMonths(1))
	private var sluttdatoDdMmAaaa: String = Date.formatToLocalDate(LocalDateTime.now().plusMonths(1))
	private var hvorMangeReisedagerHarDuPerUke: Int = 5
	private var harDuEnReiseveiPaSeksKilometerEllerMer: String = "Ja"
	private var harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde: String? = null
	private var hvorLangReiseveiHarDu: Int = 10
	private var velgLand1: VelgLand = VelgLand(
		label = "Norge", value = "NO"
	)
	private var adresse1: String = "Kongensgate 10"
	private var postnr1: String? = "3701"
	private var kanDuReiseKollektivtDagligReise: String = "Nei"
	private var hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise: Int? = null
	private var hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt: String? = "hentingEllerLeveringAvBarn"
	private var beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt: String? =
		"Mange transportbytter, ekstra lang reisetid"
	private var hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt: String? = "Ingen"
	private var kanBenytteEgenBil: KanBenytteEgenBil? = KanBenytteEgenBil(
		bompenger = 150,
		piggdekkavgift = 1000,
		ferje = null,
		annet = null,
		vilDuHaUtgifterTilParkeringPaAktivitetsstedet = "JA",
		oppgiForventetBelopTilParkeringPaAktivitetsstedet = 200,
		hvorOfteOnskerDuASendeInnKjoreliste = "UKE"
	)
	private var kanIkkeBenytteEgenBil: KanIkkeBenytteEgenBil? = null
	private var kanDuBenytteDrosje: String? = null
	private var oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIPeriodenDuSokerOmStonadFor: Int? = null
	private var hvorforKanDuIkkeBenytteDrosje: String? = null
	private var kanIkkeReiseKollektivtDagligReise: KanIkkeReiseKollektivt? =
		buildKanIkkeReiseKollektivtDagligReise()

	fun startdatoDdMmAaaa(startdatoDdMmAaaa: String) = apply { this.startdatoDdMmAaaa = startdatoDdMmAaaa }
	fun sluttdatoDdMmAaaa(sluttdatoDdMmAaaa: String) = apply { this.sluttdatoDdMmAaaa = sluttdatoDdMmAaaa }
	fun hvorMangeReisedagerHarDuPerUke(hvorMangeReisedagerHarDuPerUke: Int) =
		apply { this.hvorMangeReisedagerHarDuPerUke = hvorMangeReisedagerHarDuPerUke }

	fun harDuEnReiseveiPaSeksKilometerEllerMer(harDuEnReiseveiPaSeksKilometerEllerMer: String) =
		apply { this.harDuEnReiseveiPaSeksKilometerEllerMer = harDuEnReiseveiPaSeksKilometerEllerMer }

	fun harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde(
		harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde: String?
	) = apply {
		this.harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde =
			harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde
	}

	fun hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt(hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt: String) =
		apply { this.hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt = hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt }

	fun hvorLangReiseveiHarDu(hvorLangReiseveiHarDu: Int) = apply { this.hvorLangReiseveiHarDu = hvorLangReiseveiHarDu }
	fun velgLand1(velgLand1: VelgLand) = apply { this.velgLand1 = velgLand1 }
	fun adresse1(adresse1: String) = apply { this.adresse1 = adresse1 }
	fun postnr1(postnr1: String?) = apply { this.postnr1 = postnr1 }
	fun kanDuReiseKollektivtDagligReise(kanDuReiseKollektivtDagligReise: String) =
		apply { this.kanDuReiseKollektivtDagligReise = kanDuReiseKollektivtDagligReise }

	fun hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise(hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise: Int?) =
		apply {
			this.hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise = hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise
		}

	fun kanIkkeReiseKollektivtDagligReise(kanIkkeReiseKollektivtDagligReise: KanIkkeReiseKollektivt?) =
		apply { this.kanIkkeReiseKollektivtDagligReise = kanIkkeReiseKollektivtDagligReise }

	fun kanBenytteEgenBil(kanBenytteEgenBil: KanBenytteEgenBil?) = apply { this.kanBenytteEgenBil = kanBenytteEgenBil }
	fun kanIkkeBenytteEgenBil(kanIkkeBenytteEgenBil: KanIkkeBenytteEgenBil?) =
		apply { this.kanIkkeBenytteEgenBil = kanIkkeBenytteEgenBil }

	fun kanDuBenytteDrosje(kanDuBenytteDrosje: String?) = apply { this.kanDuBenytteDrosje = kanDuBenytteDrosje }
	fun oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIPeriodenDuSokerOmStonadFor(
		oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIPeriodenDuSokerOmStonadFor: Int?
	) = apply {
		this.oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIPeriodenDuSokerOmStonadFor =
			oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIPeriodenDuSokerOmStonadFor
	}

	fun buildKanIkkeReiseKollektivtDagligReise(): KanIkkeReiseKollektivt? {
		if (!"Ja".equals(this.kanDuReiseKollektivtDagligReise, true)) {
			return KanIkkeReiseKollektivt(
				hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt = hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt,
				beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt = "Få og upraktiske tidspunkt for avganger",
				hentingEllerLeveringAvBarn = HentingEllerLeveringAvBarn(
					adressenHvorDuHenterEllerLevererBarn = "Damfaret 10",
					postnr = "0682"
				),
				annet = null,
				kanDuBenytteEgenBil = if (kanBenytteEgenBil != null) "ja" else null,
				kanBenytteEgenBil = kanBenytteEgenBil,
				kanIkkeBenytteEgenBil = kanIkkeBenytteEgenBil,
			)
		} else {
			return null
		}
	}

	fun hvorforKanDuIkkeBenytteDrosje(hvorforKanDuIkkeBenytteDrosje: String?) =
		apply { this.hvorforKanDuIkkeBenytteDrosje = hvorforKanDuIkkeBenytteDrosje }

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
		kanIkkeReiseKollektivtDagligReise = buildKanIkkeReiseKollektivtDagligReise()
	)

}
