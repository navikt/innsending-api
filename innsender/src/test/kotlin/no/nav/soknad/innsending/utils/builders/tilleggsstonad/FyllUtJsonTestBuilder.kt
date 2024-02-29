package no.nav.soknad.innsending.utils.builders.tilleggsstonad

import no.nav.soknad.innsending.util.mapping.tilleggsstonad.*

class FyllUtJsonTestBuilder {

	val dagligReiseSkjemanr = "NAV 11-12.21B"
	val barnepassSkjemanr = "NAV 11-12.15B"
	val laeremidlerSkjemanr = "NAV 11-12.16B"
	val reiseSamlingSkjemanr = "NAV 11-12.17B"

	val defaultArenaAktivitetOgMaalgruppe = Container(
		maalgruppe = "ENSFORARBS",
		aktivitet = Aktivitet(
			aktivitetId = "123456789",
			maalgruppe = "",
			periode = SkjemaPeriode(fom = "2024-01-01", tom = "2024-06-30")
		),
		text = ""
	)

	var skjemanr: String = dagligReiseSkjemanr
	var language: String = "no-NB"
	var arenaAktivitetOgMaalgruppe: Container? = defaultArenaAktivitetOgMaalgruppe
	var flervalg: Flervalg? = null

	fun skjemanr(skjemanr: String) = apply { this.skjemanr = skjemanr }
	fun language(language: String) = apply { this.language = language }

	fun arenaAktivitetOgMaalgruppe(maalgruppe: String?, aktivitetId: String?, periode: SkjemaPeriode?) = apply {
		if (maalgruppe != null || aktivitetId != null) {
			arenaAktivitetOgMaalgruppe = Container(
				maalgruppe = maalgruppe,
				kilde = "BRUKERREGISTRERT",
				text = "",
				aktivitet = Aktivitet(aktivitetId = aktivitetId ?: "ingenAktivitet", maalgruppe = "", periode = periode)
			)
			flervalg = null
		} else {
			arenaAktivitetOgMaalgruppe = null
			flervalg = Flervalg(regArbSoker = true)
		}
	}

	fun arenaAktivitetOgMaalgruppe(arenaAktivitetOgMaalgruppe: Container?) = apply {
		this.arenaAktivitetOgMaalgruppe = arenaAktivitetOgMaalgruppe
		if (this.defaultArenaAktivitetOgMaalgruppe != null) this.flervalg = null
	}

	fun flervalg(flervalg: Flervalg?) = apply {
		this.flervalg = flervalg

		if (this.flervalg == null) arenaAktivitetOgMaalgruppe = null
	}

	var startdatoDdMmAaaa: String? = "2024-01-08"
	var sluttdatoDdMmAaaa: String? = "2024-03-29"

	fun periode(startDato: String?, sluttDato: String?) =
		apply { this.startdatoDdMmAaaa = startDato; this.sluttdatoDdMmAaaa = sluttDato }

	// Daglig reise
	var land: VelgLand? = VelgLand(label = "Norge", value = "NO")
	var adresse: String? = "Kongensgate 10"
	var postnr: String? = "3701"
	var hvorMangeReisedagerHarDuPerUke: Int? = 5
	var harDuEnReiseveiPaSeksKilometerEllerMer: String? = "ja"
	var hvorLangReiseveiHarDu: Int? = 120
	var kanDuReiseKollektivtDagligReise: String? = "nei"
	var kanReiseMedBil = KanIkkeReiseKollektivt(
		hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt = "helsemessigeArsaker",
		kanDuBenytteEgenBil = "ja",
		kanBenytteEgenBil = KanBenytteEgenBil(
			vilDuHaUtgifterTilParkeringPaAktivitetsstedet = "ja",
			hvorOfteOnskerDuASendeInnKjoreliste = "jegOnskerALevereKjorelisteEnGangIManeden",
			bompenger = 300,
			piggdekkavgift = 1000,
			ferje = 0,
			annet = 0,
			oppgiForventetBelopTilParkeringPaAktivitetsstedet = 200
		),
		annet = null,
		beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt = null,
		hentingEllerLeveringAvBarn = null,
		kanIkkeBenytteEgenBil = null
	)
	var kanIkkeReiseKollektivt: KanIkkeReiseKollektivt? = kanReiseMedBil
	var hvilkeUtgifterHarDuIForbindelseMedReisenDagligReise: Int? = null

	fun reisemal(land: VelgLand, adresse: String, postr: String) =
		apply { this.land = land; this.adresse = adresse; }

	fun reiseAvstandOgFrekvens(hvorLangReiseveiHarDu: Int?, hvorMangeReisedagerHarDuPerUke: Int?) = apply {
		harDuEnReiseveiPaSeksKilometerEllerMer = if ((hvorLangReiseveiHarDu ?: 0) >= 6) "ja" else "nei"
		this.hvorLangReiseveiHarDu = hvorLangReiseveiHarDu
		this.hvorMangeReisedagerHarDuPerUke = hvorMangeReisedagerHarDuPerUke
	}

	fun reiseKollektivt(hvilkeUtgifterHarDuIForbindelseMedReisenDagligReise: Int?) = apply {
		this.kanDuReiseKollektivtDagligReise = "ja"
		this.hvilkeUtgifterHarDuIForbindelseMedReisenDagligReise = hvilkeUtgifterHarDuIForbindelseMedReisenDagligReise
		this.kanIkkeReiseKollektivt = null
	}

	fun reiseEgenBil(kanBenytteEgenBil: KanBenytteEgenBil) = apply {
		this.kanDuReiseKollektivtDagligReise = "nei"
		this.hvilkeUtgifterHarDuIForbindelseMedReisenDagligReise = null
		this.kanIkkeReiseKollektivt = KanIkkeReiseKollektivt(
			hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt = "helsemessigeArsaker",
			kanDuBenytteEgenBil = "ja",
			kanBenytteEgenBil = kanBenytteEgenBil,
			annet = null,
			beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt = "bla, bla",
			hentingEllerLeveringAvBarn = null,
			kanIkkeBenytteEgenBil = null
		)
	}

	// Reise til samling
	var startOgSluttdatoForSamlingene: List<JsonPeriode>? = listOf(
		JsonPeriode("2014-01-30", "204-02-10"),
		JsonPeriode("2014-02-20", "204-03-01"),
		JsonPeriode("2014-03-11", "204-03-22")
	)
	var velgLandReiseTilSamling: VelgLand? = VelgLand(label = "Norge", value = "NO")
	var postnr2: String? = "6410"
	var adresse2: String? = "Kongensgate 10"
	var kanDuReiseKollektivtReiseTilSamling: String? = "nei"
	var kanReiseKollektivt: Int? = null
	var kanIkkeReiseKollektivtReiseTilSamling: KanIkkeReiseKollektivt? = KanIkkeReiseKollektivt(
		hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt = "hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt",
		beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt = "Bla bla",
		annet = null,
		kanDuBenytteEgenBil = "nei",
		kanBenytteEgenBil = null,
		kanIkkeBenytteEgenBil = KanIkkeBenytteEgenBil(
			hvaErArsakenTilAtDuIkkeKanBenytteEgenBil = "disponererIkkeBil",
			kanDuBenytteDrosje = "ja",
			oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIperiodenDuSokerOmStonadFor = 4000,
			hvilkeAndreArsakerGjorAtDuIkkeKanBenytteEgenBil = null
		),
		hentingEllerLeveringAvBarn = null
	)

	/*
		var kanReiseKollektivt: KanReiseKollektivt? = KanReiseKollektivt(
			hvilkeUtgifterHarDuIForbindelseMedReisen1 = 2000
		)
	*/

	fun reiseTilSamling(
		perioder: List<JsonPeriode>? = listOf(JsonPeriode("2014-01-30", "204-02-10")),
		kanBenytteEgenBil: KanBenytteEgenBil? = null,
		kanIkkeBenytteEgenBil: KanIkkeBenytteEgenBil? = null
	) = apply {
		this.skjemanr = reiseSamlingSkjemanr
		this.kanDuReiseKollektivtReiseTilSamling =
			if (kanBenytteEgenBil == null && kanIkkeBenytteEgenBil == null) "ja" else "nei"
		this.kanReiseKollektivt = if (kanBenytteEgenBil == null && kanIkkeBenytteEgenBil == null) 2000 else null
		this.kanIkkeReiseKollektivtReiseTilSamling = if (kanBenytteEgenBil == null && kanIkkeBenytteEgenBil == null)
			null
		else KanIkkeReiseKollektivt(
			hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt = "hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt",
			beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt = "Bla bla",
			annet = null,
			kanDuBenytteEgenBil = if (kanBenytteEgenBil != null) "ja" else "nei",
			kanBenytteEgenBil = if (kanBenytteEgenBil == null) null else kanBenytteEgenBil,
			kanIkkeBenytteEgenBil = if (kanBenytteEgenBil == null) null else kanIkkeBenytteEgenBil,
			hentingEllerLeveringAvBarn = null
		)
	}


	fun reiseDrosje(kanIkkeBenytteEgenBil: KanIkkeBenytteEgenBil) = apply {
		this.kanDuReiseKollektivtDagligReise = "nei"
		this.hvilkeUtgifterHarDuIForbindelseMedReisenDagligReise = null
		this.kanIkkeReiseKollektivt = KanIkkeReiseKollektivt(
			hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt = "helsemessigeArsaker",
			kanDuBenytteEgenBil = "nei",
			kanBenytteEgenBil = null,
			annet = null,
			beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt = "bla, bla",
			hentingEllerLeveringAvBarn = null,
			kanIkkeBenytteEgenBil = kanIkkeBenytteEgenBil
		)
	}

	// Barnepass
	var passAvBarn: List<OpplysningerOmBarn>? =
		listOf(
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
	var fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa: String? = "1990-10-10"

	fun passAvBarn(passAvBarn: List<OpplysningerOmBarn>?) = apply { this.passAvBarn = passAvBarn }
	fun fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa(fodselsdato: String?) =
		apply { fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa = fodselsdato }

	// Læremidler
	var hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore: String? =
		"videregaendeUtdanning" // hoyereUtdanning || kursEllerAnnenUtdanning
	var hvilketKursEllerAnnenFormForUtdanningSkalDuTa: String? = null // dersom kursEllerAnnenUtdanning
	var oppgiHvorMangeProsentDuStudererEllerGarPaKurs: Int? = 100 // prosent
	var harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler: String? = "nei"
	var utgifterTilLaeremidler: Int? = 10000
	var farDuDekketLaeremidlerEtterAndreOrdninger: String? = "nei" // ja || delvis
	var hvorMyeFarDuDekketAvEnAnnenAktor: Int? = null // dersom ja eller delvis
	var hvorStortBelopSokerDuOmAFaDekketAvNav: Int? = null // dersom ja eller delvis

	fun laeremidler(
		typeUtdanning: String?,
		utgifter: Int? = 10000,
		hvilketKursEllerAnnenFormForUtdanningSkalDuTa: String? = null,
		oppgiHvorMangeProsentDuStudererEllerGarPaKurs: Int? = 100,
		funksjonshemning: String? = "nei",
		farDuDekketLaeremidlerEtterAndreOrdninger: String? = "nei",
		hvorMyeFarDuDekketAvEnAnnenAktor: Int? = null,
		hvorStortBelopSokerDuOmAFaDekketAvNav: Int? = null
	) = apply {
		this.hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore = typeUtdanning
		this.utgifterTilLaeremidler = utgifter
		this.hvilketKursEllerAnnenFormForUtdanningSkalDuTa = hvilketKursEllerAnnenFormForUtdanningSkalDuTa
		this.oppgiHvorMangeProsentDuStudererEllerGarPaKurs = oppgiHvorMangeProsentDuStudererEllerGarPaKurs
		this.harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler = funksjonshemning
		this.farDuDekketLaeremidlerEtterAndreOrdninger = farDuDekketLaeremidlerEtterAndreOrdninger
		this.hvorMyeFarDuDekketAvEnAnnenAktor = hvorMyeFarDuDekketAvEnAnnenAktor
		this.hvorStortBelopSokerDuOmAFaDekketAvNav = hvorStortBelopSokerDuOmAFaDekketAvNav
	}

	fun build() =
		Root(
			language = language,
			data = ApplicationInfo(
				data = Application(
					container = arenaAktivitetOgMaalgruppe,
					// Dersom ikke målgruppe hentet fra Arena, skal søker oppgi livssituasjon
					flervalg = flervalg,

					// Personalia
					harDuNorskFodselsnummerEllerDnummer = "ja",
					fodselsnummerDnummerSoker = "10509519930",
					fornavnSoker = "Kalle",
					etternavnSoker = "Kanin",

					tilleggsopplysninger = "bla, bla",

					harRegistrertAktivitetsperiode = "ja",

					// Periode det søkes for
					startdatoDdMmAaaa = startdatoDdMmAaaa,
					sluttdatoDdMmAaaa = sluttdatoDdMmAaaa,

					// Barnepass
					opplysningerOmBarn = if (skjemanr == barnepassSkjemanr) passAvBarn else null,
					fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa = if (skjemanr == barnepassSkjemanr) fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa else null,

					// Læremidler
					hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore = if (skjemanr == laeremidlerSkjemanr) hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore else null,
					hvilketKursEllerAnnenFormForUtdanningSkalDuTa = if (skjemanr == laeremidlerSkjemanr) hvilketKursEllerAnnenFormForUtdanningSkalDuTa else null,
					oppgiHvorMangeProsentDuStudererEllerGarPaKurs = if (skjemanr == laeremidlerSkjemanr) oppgiHvorMangeProsentDuStudererEllerGarPaKurs else null,
					harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler = if (skjemanr == laeremidlerSkjemanr) harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler else null,
					utgifterTilLaeremidler = if (skjemanr == laeremidlerSkjemanr) utgifterTilLaeremidler else null,
					farDuDekketLaeremidlerEtterAndreOrdninger = if (skjemanr == laeremidlerSkjemanr) farDuDekketLaeremidlerEtterAndreOrdninger else null,
					hvorMyeFarDuDekketAvEnAnnenAktor = if (skjemanr == laeremidlerSkjemanr) hvorMyeFarDuDekketAvEnAnnenAktor else null,
					hvorStortBelopSokerDuOmAFaDekketAvNav = if (skjemanr == laeremidlerSkjemanr) hvorStortBelopSokerDuOmAFaDekketAvNav else null,

					// Daglig reise
					harDuEnReiseveiPaSeksKilometerEllerMer = if (skjemanr == dagligReiseSkjemanr) harDuEnReiseveiPaSeksKilometerEllerMer else null,
					velgLand1 = if (skjemanr == dagligReiseSkjemanr) land else null,
					adresse1 = if (skjemanr == dagligReiseSkjemanr) adresse else null,
					postnr1 = if (skjemanr == dagligReiseSkjemanr) postnr else null,
					kanDuReiseKollektivtDagligReise = if (skjemanr == dagligReiseSkjemanr) kanDuReiseKollektivtDagligReise else null,
					//visesHvisBrukerHarEnRegistrertAktivitetsperiode = null, // TODO
					hvorMangeReisedagerHarDuPerUke = if (skjemanr == dagligReiseSkjemanr) hvorMangeReisedagerHarDuPerUke else null,
					hvorLangReiseveiHarDu = if (skjemanr == dagligReiseSkjemanr) hvorLangReiseveiHarDu else null,
					hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise = if (skjemanr == dagligReiseSkjemanr) hvilkeUtgifterHarDuIForbindelseMedReisenDagligReise else null,
					kanIkkeReiseKollektivtDagligReise = if (skjemanr == dagligReiseSkjemanr) kanIkkeReiseKollektivt else null,

					// Reise til samling
					startOgSluttdatoForSamlingene = if (skjemanr == reiseSamlingSkjemanr) startOgSluttdatoForSamlingene else null,
					velgLandReiseTilSamling = if (skjemanr == reiseSamlingSkjemanr) velgLandReiseTilSamling else null,
					postnr2 = if (skjemanr == reiseSamlingSkjemanr) postnr2 else null,
					adresse2 = if (skjemanr == reiseSamlingSkjemanr) adresse2 else null,
					kanDuReiseKollektivtReiseTilSamling = if (skjemanr == reiseSamlingSkjemanr) kanDuReiseKollektivtReiseTilSamling else null,
					kanReiseKollektivt = if (skjemanr == reiseSamlingSkjemanr && kanIkkeReiseKollektivt == null) KanReiseKollektivt(
						kanReiseKollektivt
					) else null,
					kanIkkeReiseKollektivtReiseTilSamling = if (skjemanr == reiseSamlingSkjemanr) kanIkkeReiseKollektivt else null

				),
				metadata = Metadata(
					timezone = "Europe/Oslo",
					offset = 60,
					origin = "https://skjemadelingslenke.ekstern.dev.nav.no",
					referrer = "https://testid.test.idporten.no/",
					browserName = "\"Netscape\",\"userAgent\":\"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36\"",
					pathName = "/fyllut/nav111212reise/tilleggsopplysningerPanel",
					onLine = true
				),
				state = "submitted",
				vnote = ""
			)
		)

}
