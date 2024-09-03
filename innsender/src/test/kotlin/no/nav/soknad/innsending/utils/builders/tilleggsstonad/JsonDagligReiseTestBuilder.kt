package no.nav.soknad.innsending.utils.builders.tilleggsstonad

import no.nav.soknad.innsending.util.mapping.tilleggsstonad.*
import no.nav.soknad.innsending.utils.Date
import java.time.LocalDateTime

class JsonDagligReiseTestBuilder {

	private var aktivitetsinformasjon: JsonAktivitetsInformasjon? = JsonAktivitetsInformasjon(aktivitet = "1234")
	private var maalgruppeinformasjon: JsonMaalgruppeinformasjon? = JsonMaalgruppeinformasjon(
		periode = AktivitetsPeriode(startdatoDdMmAaaa = "01012024", sluttdatoDdMmAaaa = "31032024"),
		kilde = "BRUKERDEFINERT",
		maalgruppetype = "erDuArbeidssoker"
	)

	private var startdatoDdMmAaaa: String = Date.formatToLocalDate(LocalDateTime.now().minusMonths(1))
	private var sluttdatoDdMmAaaa: String = Date.formatToLocalDate(LocalDateTime.now().plusMonths(1))
	private var soknadsPeriode: SoknadsPeriode =
		SoknadsPeriode(startdato = startdatoDdMmAaaa, sluttdato = sluttdatoDdMmAaaa)
	private var hvorMangeReisedagerHarDuPerUke: Double = 5.0
	private var harDuEnReiseveiPaSeksKilometerEllerMer: String = "Ja"
	private var harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde: String? = null
	private var hvorLangReiseveiHarDu: Double = 10.0
	private var velgLand1: VelgLand = VelgLand(
		label = "Norge", value = "NO"
	)
	private var adresse1: String = "Kongensgate 10"
	private var postnr1: String? = "3701"
	private var poststed: String? = "Skien"
	private var postkode: String? = null
	private var kanDuReiseKollektivtDagligReise: String = "Nei"
	private var hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise: Double? = null
	private var hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt: String? = "hentingEllerLeveringAvBarn"
	private var beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt: String? =
		"Mange transportbytter, ekstra lang reisetid"
	private var hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt: String? = "Ingen"
	private var kanBenytteEgenBil: KanBenytteEgenBil? = KanBenytteEgenBil(
		bompenger = 150.0,
		piggdekkavgift = 1000.0,
		ferje = null,
		annet = null,
		vilDuHaUtgifterTilParkeringPaAktivitetsstedet = "JA",
		parkering = 200.0,
		hvorOfteOnskerDuASendeInnKjoreliste = "UKE"
	)
	private var kanIkkeBenytteEgenBil: KanIkkeBenytteEgenBil? = null
	private var kanDuBenytteDrosje: String? = null
	private var oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIPeriodenDuSokerOmStonadFor: Double? = null
	private var hvorforKanDuIkkeBenytteDrosje: String? = null
	private var kanIkkeReiseKollektivtDagligReise: KanIkkeReiseKollektivt? =
		buildKanIkkeReiseKollektivtDagligReise()

	fun soknadsPeriode(startdato: String, sluttdato: String) = apply {
		soknadsPeriode = SoknadsPeriode(startdato, sluttdato)
	}

	fun hvorMangeReisedagerHarDuPerUke(hvorMangeReisedagerHarDuPerUke: Double) =
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

	fun hvorLangReiseveiHarDu(hvorLangReiseveiHarDu: Double) =
		apply { this.hvorLangReiseveiHarDu = hvorLangReiseveiHarDu }

	fun velgLand1(velgLand1: VelgLand) = apply { this.velgLand1 = velgLand1 }
	fun settFullAdresse(land: VelgLand, adresse: String, postkodeEllerPostnr: String?, poststed: String?) = apply {
		this.velgLand1 = land
		this.adresse1 = adresse
		if (land.value == "NO" || land.value == "NOR") {
			this.postkode = null
			this.postnr1 = postkodeEllerPostnr
		} else {
			this.postnr1 = null
			this.postkode = postkodeEllerPostnr
		}
		this.poststed = poststed
	}

	fun adresse1(adresse1: String) = apply { this.adresse1 = adresse1 }
	fun postnr1(postnr1: String?) = apply { this.postnr1 = postnr1 }
	fun poststed(poststed: String?) = apply { this.poststed = poststed }
	fun postkode(postkode: String?) = apply { this.postkode = postkode }
	fun kanDuReiseKollektivtDagligReise(kanDuReiseKollektivtDagligReise: String) =
		apply { this.kanDuReiseKollektivtDagligReise = kanDuReiseKollektivtDagligReise }

	fun hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise(hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise: Double?) =
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
		oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIPeriodenDuSokerOmStonadFor: Double?
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
				annet = if (hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt?.equals("annet", true) == true
				) AndreArsakerIkkeKollektivt(hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt = "Andre årsaker") else null,
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
		startdatoDdMmAaaa = soknadsPeriode.startdato,
		sluttdatoDdMmAaaa = soknadsPeriode.sluttdato,
		hvorMangeReisedagerHarDuPerUke = hvorMangeReisedagerHarDuPerUke,
		harDuEnReiseveiPaSeksKilometerEllerMer = harDuEnReiseveiPaSeksKilometerEllerMer,
		harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde = harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde,
		hvorLangReiseveiHarDu = hvorLangReiseveiHarDu,
		velgLand1 = velgLand1,
		adresse1 = adresse1,
		postnr1 = postnr1,
		poststed = poststed,
		postkode = postkode,
		kanDuReiseKollektivtDagligReise = kanDuReiseKollektivtDagligReise,
		hvilkeUtgifterHarDuIForbindelseMedReisenDagligReise = hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise,
		hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt = hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt,
		kanIkkeReiseKollektivtDagligReise = buildKanIkkeReiseKollektivtDagligReise()
	)

}
