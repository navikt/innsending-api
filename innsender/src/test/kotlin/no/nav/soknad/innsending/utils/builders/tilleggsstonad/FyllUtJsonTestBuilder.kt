package no.nav.soknad.innsending.utils.builders.tilleggsstonad

import no.nav.soknad.innsending.model.MaalgruppeType
import no.nav.soknad.innsending.util.mapping.tilleggsstonad.*
import no.nav.soknad.innsending.utils.builders.tilleggsstonader.MaalgruppeTestBuilder

class FyllUtJsonTestBuilder {

	var drivingListExpences: Drivinglist? = Drivinglist(
		selectedVedtaksId = "12345678",
		dates = listOf(
			Dates(date = "2024-01-01T00:00:00.000Z", parking = "125", betalingsplanId = "21"),
			Dates(date = "2024-01-02T00:00:00.000Z", parking = "125", betalingsplanId = "21"),
			Dates(date = "2024-01-03T00:00:00.000Z", parking = "", betalingsplanId = "21"),
			Dates(date = "2024-01-08T00:00:00.000Z", parking = "125", betalingsplanId = "22"),
			Dates(date = "2024-01-09T00:00:00.000Z", parking = "125", betalingsplanId = "22"),
			Dates(date = "2024-01-10T00:00:00.000Z", parking = "", betalingsplanId = "22"),
			Dates(date = "2024-01-15T00:00:00.000Z", parking = "125", betalingsplanId = "23"),
			Dates(date = "2024-01-16T00:00:00.000Z", parking = "125", betalingsplanId = "23"),
			Dates(date = "2024-01-17T00:00:00.000Z", parking = "", betalingsplanId = "23"),
			Dates(date = "2024-01-22T00:00:00.000Z", parking = "125", betalingsplanId = "24"),
			Dates(date = "2024-01-23T00:00:00.000Z", parking = "125", betalingsplanId = "24"),
			Dates(date = "2024-01-240T00:00:00.000Z", parking = "", betalingsplanId = "24"),
		)
	)

	fun drivingListExpences(drivingListExpences: Drivinglist?) = apply {
		this.drivingListExpences = drivingListExpences
	}

	val defaultArenaAktivitetOgMaalgruppe = AktiviteterOgMaalgruppe(
		maalgruppe = MaalgruppeValg(calculated = MaalgruppeTestBuilder().build()),
		aktivitet = Aktivitet(
			aktivitetId = "123456789",
			maalgruppe = null,
			periode = SkjemaPeriode(fom = "2024-01-01", tom = "2024-06-30"),
			text = ""
		),
	)

	var skjemanr: String = reiseDaglig
	var language: String = "no-NB"
	var arenaAktivitetOgMaalgruppe: AktiviteterOgMaalgruppe? = defaultArenaAktivitetOgMaalgruppe
	var flervalg: Flervalg? = null

	fun skjemanr(skjemanr: String) = apply { this.skjemanr = skjemanr }
	fun language(language: String) = apply { this.language = language }

	fun arenaAktivitetOgMaalgruppe(maalgruppe: MaalgruppeType?, aktivitetId: String?, periode: SkjemaPeriode?) = apply {
		if (maalgruppe != null || aktivitetId != null) {
			arenaAktivitetOgMaalgruppe = AktiviteterOgMaalgruppe(
				maalgruppe = MaalgruppeValg(calculated = MaalgruppeTestBuilder().maalgruppetype(maalgruppe!!).build()),
				kilde = "BRUKERREGISTRERT",
				//text = "",
				aktivitet = Aktivitet(
					aktivitetId = aktivitetId ?: "ingenAktivitet",
					maalgruppe = null,
					periode = periode
				)
			)
			flervalg = null
		} else {
			arenaAktivitetOgMaalgruppe = null
			flervalg = Flervalg(regArbSoker = true)
		}
	}

	fun arenaAktivitetOgMaalgruppe(arenaAktivitetOgMaalgruppe: AktiviteterOgMaalgruppe?) = apply {
		this.arenaAktivitetOgMaalgruppe = arenaAktivitetOgMaalgruppe
		if (this.defaultArenaAktivitetOgMaalgruppe != null) this.flervalg = null
	}

	fun flervalg(flervalg: Flervalg?) = apply {
		this.flervalg = flervalg

		if (this.flervalg != null) arenaAktivitetOgMaalgruppe = null
	}

	var startdatoDdMmAaaa: String? = "2024-01-08"
	var sluttdatoDdMmAaaa: String? = "2024-03-29"
	var soknadsPeriode: SoknadsPeriode? = SoknadsPeriode(startdato = "2024-01-01", sluttdato = "2024-03-31")

	fun periode(startDato: String?, sluttDato: String?) =
		apply {
			this.startdatoDdMmAaaa = startDato
			this.sluttdatoDdMmAaaa = sluttDato
			if (startDato != null && sluttDato != null)
				this.soknadsPeriode = SoknadsPeriode(startdato = startDato, sluttdato = sluttDato)
			else
				this.soknadsPeriode = null
		}

	// Daglig reise
	var land: VelgLand? = VelgLand(label = "Norge", value = "NO")
	var adresse: String? = "Kongensgate 10"
	var postnr: String? = "3701"
	var poststed: String? = "Skien"
	var postkode: String? = if (land != null && !land?.value.equals("NO", true)) "UK-15074 Hampthon Park" else null
	var hvorMangeReisedagerHarDuPerUke: Double? = 5.0
	var harDuEnReiseveiPaSeksKilometerEllerMer: String? = "ja"
	var harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde: String? = "nei"
	var hvorLangReiseveiHarDu: Double? = 120.0
	var kanDuReiseKollektivtDagligReise: String? = "nei"
	var kanReiseMedBil = KanIkkeReiseKollektivt(
		hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt = "helsemessigeArsaker",
		kanDuBenytteEgenBil = "ja",
		kanBenytteEgenBil = KanBenytteEgenBil(
			vilDuHaUtgifterTilParkeringPaAktivitetsstedet = "ja",
			hvorOfteOnskerDuASendeInnKjoreliste = "jegOnskerALevereKjorelisteEnGangIManeden",
			bompenger = 300.0,
			piggdekkavgift = 1000.0,
			ferje = 0.0,
			annet = 0.0,
			parkering = 200.0
		),
		beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt = null,
		hentingEllerLeveringAvBarn = null,
		kanIkkeBenytteEgenBil = null
	)
	var kanIkkeReiseKollektivt: KanIkkeReiseKollektivt? = kanReiseMedBil
	var hvilkeUtgifterHarDuIForbindelseMedReisenDagligReise: Double? = null

	fun reisemal(land: VelgLand, adresse: String, postr: String? = "3701", poststed: String? = "Skien") =
		apply { this.land = land; this.adresse = adresse; this.poststed = poststed }

	fun harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde(
		harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde: String?
	) =
		apply {
			this.harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde =
				harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde
		}

	fun reiseAvstandOgFrekvens(hvorLangReiseveiHarDu: Double?, hvorMangeReisedagerHarDuPerUke: Double?) = apply {
		harDuEnReiseveiPaSeksKilometerEllerMer = if ((hvorLangReiseveiHarDu ?: 0.0) >= 6.0) "ja" else "nei"
		this.hvorLangReiseveiHarDu = hvorLangReiseveiHarDu
		this.hvorMangeReisedagerHarDuPerUke = hvorMangeReisedagerHarDuPerUke
	}

	fun reiseKollektivt(hvilkeUtgifterHarDuIForbindelseMedReisenDagligReise: Double?) = apply {
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
	var kanReiseKollektivt: Double? = null
	var kanIkkeReiseKollektivtReiseTilSamling: KanIkkeReiseKollektivt? = KanIkkeReiseKollektivt(
		hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt = "hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt",
		beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt = "Bla bla",
		kanDuBenytteEgenBil = "nei",
		kanBenytteEgenBil = null,
		kanIkkeBenytteEgenBil = KanIkkeBenytteEgenBil(
			hvaErArsakenTilAtDuIkkeKanBenytteEgenBil = "disponererIkkeBil",
			kanDuBenytteDrosje = "ja",
			oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIperiodenDuSokerOmStonadFor = 4000.0,
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
		this.skjemanr = reiseSamling
		this.kanDuReiseKollektivtReiseTilSamling =
			if (kanBenytteEgenBil == null && kanIkkeBenytteEgenBil == null) "ja" else "nei"
		this.kanReiseKollektivt = if (kanBenytteEgenBil == null && kanIkkeBenytteEgenBil == null) 2000.0 else null
		this.kanIkkeReiseKollektivtReiseTilSamling = if (kanBenytteEgenBil == null && kanIkkeBenytteEgenBil == null)
			null
		else KanIkkeReiseKollektivt(
			hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt = "helsemessigeArsaker",
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
			hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt = "annet",
			annet = AndreArsakerIkkeKollektivt(hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt = "Bla bla"),
			kanDuBenytteEgenBil = "nei",
			kanBenytteEgenBil = null,
			beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt = "bla, bla",
			hentingEllerLeveringAvBarn = null,
			kanIkkeBenytteEgenBil = kanIkkeBenytteEgenBil
		)
	}

	// Barnepass
	var passAvBarna: List<OpplysningerOmBarn>? =
		listOf(
			OpplysningerOmBarn(
				fornavn = "Lite",
				etternavn = "Barn",
				fodselsdatoDdMmAaaa = "2019-03-07",
				jegSokerOmStonadTilPassAvDetteBarnet = true,
				fodselsnummerDNummer = "23922399883",
				sokerStonadForDetteBarnet = SokerStonadForDetteBarnet(
					hvemPasserBarnet = "barnehageEllerSfo",
					oppgiManedligUtgiftTilBarnepass = 6000.0,
					harBarnetFullfortFjerdeSkolear = "nei",
					hvaErArsakenTilAtBarnetDittTrengerPass = null
				)
			)
		)
	var fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa: String? = "1990-10-10"
	var fodselsnummerDNummerAndreForelder: String? = "16905198584"

	fun passAvBarn(passAvBarn: List<OpplysningerOmBarn>?) = apply { this.passAvBarna = passAvBarn }
	fun fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa(fodselsdato: String?) =
		apply { fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa = fodselsdato }

	fun fodselsnummerDNummerAndreForelder(fnr: String?) =
		apply { fodselsnummerDNummerAndreForelder = fnr }

	// Læremidler
	var hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore: String? =
		"videregaendeUtdanning" // hoyereUtdanning || kursEllerAnnenUtdanning
	var hvilketKursEllerAnnenFormForUtdanningSkalDuTa: String? = null // dersom kursEllerAnnenUtdanning
	var oppgiHvorMangeProsentDuStudererEllerGarPaKurs: Double? = 100.0 // prosent
	var harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler: String? = "nei"
	var utgifterTilLaeremidler: Double? = 10000.0
	var farDuDekketLaeremidlerEtterAndreOrdninger: String? = "nei" // ja || delvis
	var hvorMyeFarDuDekketAvEnAnnenAktor: Double? = null // dersom ja eller delvis
	var hvorStortBelopSokerDuOmAFaDekketAvNav: Double? = null // dersom ja eller delvis

	fun laeremidler(
		typeUtdanning: String?,
		utgifter: Double? = 10000.0,
		hvilketKursEllerAnnenFormForUtdanningSkalDuTa: String? = null,
		oppgiHvorMangeProsentDuStudererEllerGarPaKurs: Double? = 100.0,
		funksjonshemning: String? = "nei",
		farDuDekketLaeremidlerEtterAndreOrdninger: String? = "nei",
		hvorMyeFarDuDekketAvEnAnnenAktor: Double? = null,
		hvorStortBelopSokerDuOmAFaDekketAvNav: Double? = null
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

	var startdato: String? = null
	var sluttdato: String? = null

	fun periodeReiseTilSamling(startdato: String?, sluttdato: String?) = apply {
		this.startdato = startdato
		this.sluttdato = sluttdato
	}

	fun build() =
		Root(
			language = language,
			data = ApplicationInfo(
				data = Application(
					aktiviteterOgMaalgruppe = arenaAktivitetOgMaalgruppe,
					// Dersom ikke målgruppe hentet fra Arena, skal søker oppgi livssituasjon
					flervalg = flervalg,

					// Dropper Personinfo da dette ikke brukes i konvertering til XML

					tilleggsopplysninger = "bla, bla",

					// Periode det søkes for
					startdatoDdMmAaaa = startdatoDdMmAaaa,
					sluttdatoDdMmAaaa = sluttdatoDdMmAaaa,
					soknadsPeriode = soknadsPeriode,
					//soknadsperiode1 = soknadsPeriode,
					startdato = startdato,
					sluttdato = sluttdato,

					// Barnepass
					opplysningerOmBarn = if (skjemanr == stotteTilPassAvBarn) passAvBarna else null,
					fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa = if (skjemanr == stotteTilPassAvBarn) fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa else null,
					fodselsnummerDNummerAndreForelder = if (skjemanr == stotteTilPassAvBarn) fodselsnummerDNummerAndreForelder else null,

					// Læremidler
					hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore =
					if (skjemanr == stotteTilLaeremidler) hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore else null,
					hvilketKursEllerAnnenFormForUtdanningSkalDuTa = if (skjemanr == stotteTilLaeremidler) hvilketKursEllerAnnenFormForUtdanningSkalDuTa else null,
					oppgiHvorMangeProsentDuStudererEllerGarPaKurs = if (skjemanr == stotteTilLaeremidler) oppgiHvorMangeProsentDuStudererEllerGarPaKurs else null,
					harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler = if (skjemanr == stotteTilLaeremidler) harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler else null,
					utgifterTilLaeremidler = if (skjemanr == stotteTilLaeremidler) utgifterTilLaeremidler else null,
					farDuDekketLaeremidlerEtterAndreOrdninger = if (skjemanr == stotteTilLaeremidler) farDuDekketLaeremidlerEtterAndreOrdninger else null,
					hvorMyeFarDuDekketAvEnAnnenAktor = if (skjemanr == stotteTilLaeremidler) hvorMyeFarDuDekketAvEnAnnenAktor else null,
					hvorStortBelopSokerDuOmAFaDekketAvNav = if (skjemanr == stotteTilLaeremidler) hvorStortBelopSokerDuOmAFaDekketAvNav else null,

					// Daglig reise
					harDuEnReiseveiPaSeksKilometerEllerMer = if (skjemanr == reiseDaglig) harDuEnReiseveiPaSeksKilometerEllerMer else null,
					velgLand1 = if (skjemanr == reiseDaglig) land else null,
					adresse1 = if (skjemanr == reiseDaglig) adresse else null,
					postnr1 = if (skjemanr == reiseDaglig) postnr else null,
					poststed = poststed,
					postkode = postkode,
					kanDuReiseKollektivtDagligReise = if (skjemanr == reiseDaglig) kanDuReiseKollektivtDagligReise else null,
					//visesHvisBrukerHarEnRegistrertAktivitetsperiode = null, // TODO
					hvorMangeReisedagerHarDuPerUke = if (skjemanr == reiseDaglig) hvorMangeReisedagerHarDuPerUke else null,
					hvorLangReiseveiHarDu = if (skjemanr == reiseDaglig) hvorLangReiseveiHarDu else null,
					harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde = if (skjemanr == reiseDaglig) harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde else null,
					hvilkeUtgifterHarDuIForbindelseMedReisenDagligReise = if (skjemanr == reiseDaglig) hvilkeUtgifterHarDuIForbindelseMedReisenDagligReise else null,
					kanIkkeReiseKollektivtDagligReise = if (skjemanr == reiseDaglig) kanIkkeReiseKollektivt else null,

					// Reise til samling
					startOgSluttdatoForSamlingene = if (skjemanr == reiseSamling) startOgSluttdatoForSamlingene else null,
					velgLandReiseTilSamling = if (skjemanr == reiseSamling) velgLandReiseTilSamling else null,
					postnr2 = if (skjemanr == reiseSamling) postnr2 else null,
					adresse2 = if (skjemanr == reiseSamling) adresse2 else null,
					kanDuReiseKollektivtReiseTilSamling = if (skjemanr == reiseSamling) kanDuReiseKollektivtReiseTilSamling else null,
					kanReiseKollektivt = if (skjemanr == reiseSamling && kanIkkeReiseKollektivt == null) KanReiseKollektivt(
						kanReiseKollektivt
					) else null,
					kanIkkeReiseKollektivtReiseTilSamling = if (skjemanr == reiseSamling) kanIkkeReiseKollektivt else null,

					// DrivingListExpences
					drivinglist = if (skjemanr == kjoreliste) drivingListExpences else null

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
