package no.nav.soknad.innsending.utils.builders.tilleggsstonad

import no.nav.soknad.innsending.util.mapping.tilleggsstonad.*

class FyllUtJsonTestBuilder {

	val defaultMaalgruppeInformasjon = JsonMaalgruppeinformasjon(
		periode = AktivitetsPeriode(startdatoDdMmAaaa = "2024-01-01", sluttdatoDdMmAaaa = "2024-06-20"),
		kilde = "BRUKERDEFINERT", "ENSFORARBS"
	)

	var language: String = "no-NB"
	var aktivitetsId: String? = "123456789"
	var arenaMaalgruppe: JsonMaalgruppeinformasjon? = defaultMaalgruppeInformasjon
	var maalgrupper: Map<String, String>? = emptyMap()

	fun arenaMaalgruppe(hentetFraArena: JsonMaalgruppeinformasjon?) = apply {
		arenaMaalgruppe = hentetFraArena
		maalgrupper = if (hentetFraArena != null) emptyMap() else mutableMapOf("erDuArbeidssoker" to "ja")
	}

	fun maalgrupper(maalgrupper: Map<String, String>?) = apply {
		this.maalgrupper = maalgrupper
		arenaMaalgruppe = if (maalgrupper != null) null else defaultMaalgruppeInformasjon
	}

	fun language(language: String) = apply { this.language = language }

	fun aktivitetsId(aktivitetsId: String) = apply { this.aktivitetsId = aktivitetsId }

	fun build() =
		Root(
			language = language,
			data = ApplicationInfo(
				data = Application(
					aktivitetsId = aktivitetsId,
					maalgruppeType = arenaMaalgruppe?.maalgruppetype,
					maalgruppePeriode = if (arenaMaalgruppe?.periode == null)
						null else
						JsonPeriode(
							startdatoDdMmAaaa = arenaMaalgruppe?.periode?.startdatoDdMmAaaa ?: "2024-01-01",
							sluttdatoDdMmAaaa = arenaMaalgruppe?.periode?.sluttdatoDdMmAaaa ?: "2024-06-20"
						),
					erDuArbeidssoker = maalgrupper?.get("erDuArbeidssoker"),
					mottarDuEllerHarDuSoktOmDagpenger = maalgrupper?.get("mottarDuEllerHarDuSoktOmDagpenger"),
					mottarDuEllerHarDuSoktOmTiltakspenger = maalgrupper?.get("mottarDuEllerHarDuSoktOmTiltakspenger"),
					gjennomforerDuEnUtdanningSomNavHarGodkjent = maalgrupper?.get("gjennomforerDuEnUtdanningSomNavHarGodkjent"),
					erDuGjenlevendeEktefelle = maalgrupper?.get("erDuGjenlevendeEktefelle"),
					erDuTidligereFamiliepleier = maalgrupper?.get("erDuTidligereFamiliepleier"),
					erDuUgiftSkiltEllerSeparertOgErAleneOmOmsorgenForBarn = maalgrupper?.get("erDuUgiftSkiltEllerSeparertOgErAleneOmOmsorgenForBarn"),
					erDuUgiftSkiltEllerSeparertOgErAleneOmOmsorgenForBarn1 = maalgrupper?.get("erDuUgiftSkiltEllerSeparertOgErAleneOmOmsorgenForBarn1"),
					nedsattArbeidsevnePgaSykdom = NedsattArbeidsevnePgaSykdom(
						harDuNedsattArbeidsevnePaGrunnAvSykdom = maalgrupper?.get("harDuNedsattArbeidsevnePaGrunnAvSykdom"),
						harDuVedtakFraNavOmNedsattArbeidsevnePaGrunnAvSykdom = maalgrupper?.get("harDuVedtakFraNavOmNedsattArbeidsevnePaGrunnAvSykdom"),
						mottarDuSykepenger = maalgrupper?.get("mottarDuSykepenger"),
						mottarDuLonnFraArbeidsgiverMensDuGjennomforerEnAktivitetSomNavHarGodkjent = maalgrupper?.get("mottarDuLonnFraArbeidsgiverMensDuGjennomforerEnAktivitetSomNavHarGodkjent")
					),
					annet1 = maalgrupper?.get("annet1"),

					harDuNorskFodselsnummerEllerDnummer = "ja",
					fodselsnummerDnummerSoker = "10509519930",
					fornavnSoker = "Kalle",
					etternavnSoker = "Kanin",

					tilleggsopplysninger = "bla, bla",

					harRegistrertAktivitetsperiode = "ja",
					startdatoDdMmAaaa = "2024-01-08",
					sluttdatoDdMmAaaa = "2024-03-29",
					harDuEnReiseveiPaSeksKilometerEllerMer = "ja",
					velgLand1 = VelgLand(
						label = "Norge",
						value = "NO"
					),
					adresse1 = "Kongensgate 10",
					kanDuReiseKollektivtDagligReise = "nei",
					//visesHvisBrukerHarEnRegistrertAktivitetsperiode = null, // TODO
					hvorMangeReisedagerHarDuPerUke = 5,
					hvorLangReiseveiHarDu = 120,
					postnr1 = "3701",
					kanIkkeReiseKollektivtDagligReise = KanIkkeReiseKollektivt(
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
