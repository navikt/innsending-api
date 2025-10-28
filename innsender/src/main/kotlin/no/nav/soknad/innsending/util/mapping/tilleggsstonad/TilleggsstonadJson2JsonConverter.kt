package no.nav.soknad.innsending.util.mapping.tilleggsstonad

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.Maalgruppe


fun convertToJsonTilleggsstonad(soknadDto: DokumentSoknadDto, json: ByteArray?): JsonApplication<JsonTilleggsstonad> {
	if (json == null || json.isEmpty())
		throw BackendErrorException("${soknadDto.innsendingsId}: json fil av søknaden mangler")

	val mapper = jacksonObjectMapper().findAndRegisterModules()
	val json = mapper.readValue(json, Root::class.java)

	return JsonApplication(
		timezone = json.data.metadata?.timezone,
		language = json.language,
		applicationDetails = convertToJsonTilleggsstonad(json.data.data, soknadDto)
	)
}

fun convertToJsonTilleggsstonad(tilleggsstonad: Application, soknadDto: DokumentSoknadDto): JsonTilleggsstonad {
	return JsonTilleggsstonad(
		aktivitetsinformasjon = convertAktivitetsinformasjon(tilleggsstonad),
		maalgruppeinformasjon = validateNotNull(
			convertToJsonMaalgruppeinformasjon(
				tilleggsstonad.aktiviteterOgMaalgruppe,
				tilleggsstonad.flervalg,
				tilleggsstonad.regArbSoker
			), "Mangler: 'Målgruppde informasjon'"
		),
		rettighetstype = convertToJsonRettighetstyper(tilleggsstonad, soknadDto)

	)
}

private fun convertAktivitetsinformasjon(tilleggsstonad: Application): JsonAktivitetsInformasjon? {
	return if (tilleggsstonad.aktiviteterOgMaalgruppe != null
		&& tilleggsstonad.aktiviteterOgMaalgruppe.aktivitet?.aktivitetId != null
		&& tilleggsstonad.aktiviteterOgMaalgruppe.aktivitet.aktivitetId != "ingenAktivitet"
	)
		JsonAktivitetsInformasjon(aktivitet = tilleggsstonad.aktiviteterOgMaalgruppe.aktivitet.aktivitetId)
	else
		null
}

fun getMaalgruppeInformasjonFromAktiviteterOgMaalgruppe(aktiviteterOgMaalgruppe: AktiviteterOgMaalgruppe?): JsonMaalgruppeinformasjon? {
	if (aktiviteterOgMaalgruppe == null) return null

	val maalgruppe = getSelectedMaalgruppe(aktiviteterOgMaalgruppe)
	if (maalgruppe == null) return null

	return JsonMaalgruppeinformasjon(
		periode = if (maalgruppe.gyldighetsperiode != null) AktivitetsPeriode(
			maalgruppe.gyldighetsperiode!!.fom.toString(),
			maalgruppe.gyldighetsperiode!!.tom?.toString()
		) else null,
		maalgruppetype = maalgruppe.maalgruppetype.value
	)
}

fun getSelectedMaalgruppe(aktiviteterOgMaalgruppe: AktiviteterOgMaalgruppe): Maalgruppe? {
	if (aktiviteterOgMaalgruppe.aktivitet?.maalgruppe != null) return aktiviteterOgMaalgruppe.aktivitet.maalgruppe
	if (aktiviteterOgMaalgruppe.maalgruppe?.prefilled != null) return aktiviteterOgMaalgruppe.maalgruppe.prefilled
	if (aktiviteterOgMaalgruppe.maalgruppe?.calculated != null) return aktiviteterOgMaalgruppe.maalgruppe.calculated

	return null
}

fun convertToJsonMaalgruppeinformasjon(
	aktiviteterOgMaalgruppe: AktiviteterOgMaalgruppe?,
	flervalg: Flervalg?,
	regArbSoker: String?
): JsonMaalgruppeinformasjon? {

	return getMaalgruppeInformasjonFromAktiviteterOgMaalgruppe(aktiviteterOgMaalgruppe)
		?: getMaalgruppeinformasjonFromLivssituasjon(flervalg, regArbSoker)
}

fun getMaalgruppeinformasjonFromLivssituasjon(
	livssituasjon: Flervalg?,
	regArbSoker: String?
): JsonMaalgruppeinformasjon? {
	// Bruk maalgruppeinformasjon hvis dette er hentet fra Arena og lagt inn på søknaden

	if (livssituasjon == null) return null

	// Basert på søker sin spesifisering av livssituasjon, avled prioritert målgruppe
	// Pri 1
	if (livssituasjon.aapUforeNedsattArbEvne == true)
		return JsonMaalgruppeinformasjon(
			periode = null,
			kilde = "BRUKERREGISTRERT",
			maalgruppetype = "NEDSARBEVN"
		)

	// Pri 2
	if (livssituasjon.ensligUtdanning == true)
		return JsonMaalgruppeinformasjon(
			periode = null,
			kilde = "BRUKERREGISTRERT",
			maalgruppetype = "ENSFORUTD"
		)

	// Pri 3
	if (livssituasjon.ensligArbSoker == true)
		return JsonMaalgruppeinformasjon(
			periode = null,
			kilde = "BRUKERREGISTRERT",
			maalgruppetype = "ENSFORARBS"
		)

	// Pri 4
	if (livssituasjon.tidligereFamiliepleier == true)
		return JsonMaalgruppeinformasjon(
			periode = null,
			kilde = "BRUKERREGISTRERT",
			maalgruppetype = "TIDLFAMPL"
		)

	// Pri 5
	if (livssituasjon.gjenlevendeUtdanning == true)
		return JsonMaalgruppeinformasjon(
			periode = null,
			kilde = "BRUKERREGISTRERT",
			maalgruppetype = "GJENEKUTD"
		)

	// Pri 6
	if (livssituasjon.gjenlevendeArbSoker == true)
		return JsonMaalgruppeinformasjon(
			periode = null,
			kilde = "BRUKERREGISTRERT",
			maalgruppetype = "GJENEKARBS"
		)

	// Pri 7
	if (livssituasjon.tiltakspenger == true)
		return JsonMaalgruppeinformasjon(
			periode = null,
			kilde = "BRUKERREGISTRERT",
			maalgruppetype = "MOTTILTPEN"
		)

	// Pri 8
	if (livssituasjon.dagpenger == true)
		return JsonMaalgruppeinformasjon(periode = null, kilde = "BRUKERREGISTRERT", maalgruppetype = "MOTDAGPEN")

	// Pri 9
	if (livssituasjon.regArbSoker == true || "ja".equals(regArbSoker, true)
	)
		return JsonMaalgruppeinformasjon(periode = null, kilde = "BRUKERREGISTRERT", maalgruppetype = "ARBSOKERE")

	// Pri 10
	if (livssituasjon.annet == true || "nei".equals(regArbSoker, true))
		return JsonMaalgruppeinformasjon(
			periode = null,
			kilde = "BRUKERREGISTRERT",
			maalgruppetype = "ANNET"
		)
	/* Setter default målgruppe for å kunne sende inn ungdomsprogrammer */
	return JsonMaalgruppeinformasjon(
		periode = null,
		kilde = "BRUKERREGISTRERT",
		maalgruppetype = "ANNET"
	)
}

private fun convertToJsonRettighetstyper(
	tilleggsstonad: Application,
	soknadDto: DokumentSoknadDto
): JsonRettighetstyper {
	return JsonRettighetstyper(
		reise = convertToReisestottesoknad(tilleggsstonad, soknadDto),
		tilsynsutgifter = convertToTilsynsutgifter(tilleggsstonad, soknadDto),
		laeremiddelutgifter = convertToLaeremiddelutgifter(tilleggsstonad, soknadDto),
		bostotte = convertToJsonBostotte(tilleggsstonad, soknadDto),
		flytteutgifter = convertToJsonFlytteutgifter(tilleggsstonad, soknadDto)
	)
}

private fun convertToJsonFlytteutgifter(
	tilleggsstonad: Application,
	soknadDto: DokumentSoknadDto
): JsonFlytteutgifter? {
	if (soknadDto.skjemanr != stotteTilFlytting) return null

	return JsonFlytteutgifter(
		aktivitetsperiode = JsonPeriode(
			startdatoDdMmAaaa = tilleggsstonad.aktiviteterOgMaalgruppe?.aktivitet?.periode?.fom
				?: getSelectedDate(tilleggsstonad.narFlytterDuDdMmAaaa, null, "Mangler: 'Flyttedato"),
			sluttdatoDdMmAaaa = tilleggsstonad.aktiviteterOgMaalgruppe?.aktivitet?.periode?.tom
				?: getSelectedDate(tilleggsstonad.narFlytterDuDdMmAaaa, null, "Mangler: 'Flyttedato")
		),
		hvorforFlytterDu = validateNotNull(tilleggsstonad.hvorforFlytterDu, "Mangler: 'Hvorfor flytter du'"),
		narFlytterDuDdMmAaaa = getSelectedDate(tilleggsstonad.narFlytterDuDdMmAaaa, null, "Mangler: 'Flyttedato"),
		oppgiForsteDagINyJobbDdMmAaaa = tilleggsstonad.oppgiForsteDagINyJobbDdMmAaaa,
		erBostedEtterFlytting = tilleggsstonad.detteErAdressenJegSkalBoPaEtterAtJegHarFlyttet != null,
		velgLand1 = tilleggsstonad.velgLand1 ?: VelgLand(label = "Norge", "NO"), // Default Norge
		adresse1 = validateNotNull(tilleggsstonad.adresse1, "Daglig reise adresse"),
		postnr1 = tilleggsstonad.postnr1,
		poststed = tilleggsstonad.poststed,
		postkode = tilleggsstonad.postkode,
		farDuDekketUtgifteneDineTilFlyttingPaAnnenMateEnnMedStonadFraNav = validateNotNull(
			tilleggsstonad.farDuDekketUtgifteneDineTilFlyttingPaAnnenMateEnnMedStonadFraNav,
			"Mangler: 'Får du dekket utgiftene dine til flytting på annen måte enn med stønad fra NAV'"
		),
		ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra = validateNotNull(
			tilleggsstonad.ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra,
			"Mangler: 'Ordner du flytting selv eller kommer du til å bruke flyttebyrå mangler'"
		),
		jegFlytterSelv = tilleggsstonad.jegFlytterSelv,
		jegVilBrukeFlyttebyra = tilleggsstonad.jegVilBrukeFlyttebyra,
		jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv = tilleggsstonad.jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv
	)
}

private fun convertToJsonBostotte(tilleggsstonad: Application, soknadDto: DokumentSoknadDto): JsonBostottesoknad? {
	if (soknadDto.skjemanr != stotteTilBolig) return null

	return JsonBostottesoknad(
		aktivitetsperiode = JsonPeriode(
			getSelectedDate(tilleggsstonad.startdatoDdMmAaaa, null, "Mangler: 'Start dato for bostøtte'"),
			getSelectedDate(tilleggsstonad.sluttdatoDdMmAaaa, null, "Mangler: 'Slutt dato for bostøtte'")
		),
		hvilkeBoutgifterSokerDuOmAFaDekket = validateNotNull(
			tilleggsstonad.hvilkeBoutgifterSokerDuOmAFaDekket,
			"Mangler: 'Hvilke boutgifter søker du om å få dekket'"
		),
		bostotteIForbindelseMedSamling = tilleggsstonad.bostotteIForbindelseMedSamling,
		mottarDuBostotteFraKommunen = if (tilleggsstonad.hvilkeBoutgifterSokerDuOmAFaDekket == "fasteBoutgifter") validateNotNull(
			tilleggsstonad.mottarDuBostotteFraKommunen,
			"'Mottar du bostøtte fra kommunen'"
		) else null, // "Ja" | "Nei"
		bostottebelop = tilleggsstonad.hvorMyeBostotteMottarDu,
		hvilkeAdresserHarDuBoutgifterPa = validateNotNull(
			tilleggsstonad.hvilkeAdresserHarDuBoutgifterPa,
			"'Hvilke adresser har du utgifter på'"
		),
		boutgifterPaHjemstedetMitt = tilleggsstonad.boutgifterPaHjemstedetMitt,
		boutgifterPaAktivitetsadressen = tilleggsstonad.boutgifterPaAktivitetsadressen,
		boutgifterJegHarHattPaHjemstedetMittMenSomHarOpphortIForbindelseMedAktiviteten = tilleggsstonad.boutgifterJegHarHattPaHjemstedetMittMenSomHarOpphortIForbindelseMedAktiviteten,
		erDetMedisinskeForholdSomPavirkerUtgifteneDinePaAktivitetsstedet = tilleggsstonad.erDetMedisinskeForholdSomPavirkerUtgifteneDinePaAktivitetsstedet
	)
}

private fun convertToLaeremiddelutgifter(
	tilleggsstonad: Application,
	soknadDto: DokumentSoknadDto
): JsonLaeremiddelutgifter? {
	if (soknadDto.skjemanr != stotteTilLaeremidler) return null

	return JsonLaeremiddelutgifter(
		aktivitetsperiode = JsonPeriode(
			getSelectedDate(tilleggsstonad.startdatoDdMmAaaa, null, "'Start dato læremidler'"),
			getSelectedDate(tilleggsstonad.sluttdatoDdMmAaaa, null, "'Slutt dato læremidler'")
		),
		hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore = validateNotNull(
			tilleggsstonad.hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore,
			"'Hvilke type utdanning eller opplæring skal du gjennomføre'"
		),
		hvilketKursEllerAnnenFormForUtdanningSkalDuTa = tilleggsstonad.hvilketKursEllerAnnenFormForUtdanningSkalDuTa,
		oppgiHvorMangeProsentDuStudererEllerGarPaKurs = tilleggsstonad.oppgiHvorMangeProsentDuStudererEllerGarPaKurs
			?: 0.0,
		harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler = tilleggsstonad.harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler
			?: "Nei",
		utgifterTilLaeremidler = tilleggsstonad.utgifterTilLaeremidler ?: 0.0,
		farDuDekketLaeremidlerEtterAndreOrdninger = validateNotNull(
			tilleggsstonad.farDuDekketLaeremidlerEtterAndreOrdninger,
			"'Får du dekket læremidler etter andre ordninger'"
		),
		hvorMyeFarDuDekketAvEnAnnenAktor = tilleggsstonad.hvorMyeFarDuDekketAvEnAnnenAktor,
		hvorStortBelopSokerDuOmAFaDekketAvNav = tilleggsstonad.hvorStortBelopSokerDuOmAFaDekketAvNav
	)
}

private fun convertToTilsynsutgifter(tilleggsstonad: Application, soknadDto: DokumentSoknadDto): JsonTilsynsutgifter? {
	if (soknadDto.skjemanr != stotteTilPassAvBarn) return null

	val opplysningerOmBarn = validateNotNull(tilleggsstonad.opplysningerOmBarn, "'Opplysninger om barn'")

	return JsonTilsynsutgifter(
		aktivitetsPeriode = JsonPeriode(
			getSelectedDate(tilleggsstonad.startdatoDdMmAaaa, null, "'Start dato for pass av barn'"),
			getSelectedDate(tilleggsstonad.sluttdatoDdMmAaaa, null, "'Slutt dato for pass av barn'")
		),

		barnePass = opplysningerOmBarn.map {
			BarnePass(
				fornavn = it.fornavn,
				etternavn = it.etternavn,
				fodselsdatoDdMmAaaa = validateNotNull(
					it.fodselsnummerDNummer,
					"Tilsynsutgifter - fødselsdato/fnr mangler"
				),
				jegSokerOmStonadTilPassAvDetteBarnet = it.jegSokerOmStonadTilPassAvDetteBarnet,
				sokerStonadForDetteBarnet = it.sokerStonadForDetteBarnet,
			)
		},
		fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa = tilleggsstonad.fodselsnummerDNummerAndreForelder
			?: tilleggsstonad.fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa
	)
}

private fun erReisestottesoknad(skjemanr: String): Boolean {
	return reisestotteskjemaer.contains(skjemanr.substring(0, reisestotteskjemaer[0].length))
}


private fun convertToReisestottesoknad(
	tilleggsstonad: Application,
	soknadDto: DokumentSoknadDto
): JsonReisestottesoknad? {
	if (!erReisestottesoknad(soknadDto.skjemanr)) return null
	return JsonReisestottesoknad(
		dagligReise = if (soknadDto.skjemanr.startsWith(reiseDaglig) || soknadDto.skjemanr.startsWith(ungdomsprogram_reiseDaglig))
			convertToJsonDagligReise(tilleggsstonad) else null,
		reiseSamling = if (soknadDto.skjemanr.startsWith(reiseSamling) || soknadDto.skjemanr.startsWith(ungdomsprogram_reiseSamling))
			convertToJsonReiseSamling(tilleggsstonad) else null,
		dagligReiseArbeidssoker = if (soknadDto.skjemanr.startsWith(reiseArbeid))
			convertToJsonReiseArbeidssoker(tilleggsstonad) else null,
		oppstartOgAvsluttetAktivitet = if (soknadDto.skjemanr.startsWith(reiseOppstartSlutt) ||  soknadDto.skjemanr.startsWith(ungdomsprogram_reiseOppstartSlutt))
			convertToJsonOppstartOgAvsluttetAktivitet(tilleggsstonad) else null
	)
}

private fun getSelectedDate(userDate: String?, activityDate: String?, field: String): String {
	if (userDate != null && userDate.isNotEmpty()) return userDate
	return validateNotNull(activityDate, field)
}

private fun convertToJsonDagligReise(tilleggsstonad: Application): JsonDagligReise {
	val harDuReiseveiPaMerEnn6Km = validateNotNull(
		tilleggsstonad.harDuEnReiseveiPaSeksKilometerEllerMer,
		"Daglig reise avstand mer enn 6 km"
	) // JA|NEI
	return JsonDagligReise(
		startdatoDdMmAaaa = getSelectedDate(
			tilleggsstonad.soknadsPeriode?.startdato ?: tilleggsstonad.startdato,
			tilleggsstonad.aktiviteterOgMaalgruppe?.aktivitet?.periode?.fom,
			"DagligReise startdato"
		),
		sluttdatoDdMmAaaa = getSelectedDate(
			tilleggsstonad.soknadsPeriode?.sluttdato ?: tilleggsstonad.sluttdato,
			tilleggsstonad.aktiviteterOgMaalgruppe?.aktivitet?.periode?.tom,
			"DagligReise sluttdato"
		),
		hvorMangeReisedagerHarDuPerUke = tilleggsstonad.hvorMangeReisedagerHarDuPerUke,
		hvorLangReiseveiHarDu = validateNotNull(
			tilleggsstonad.hvorLangReiseveiHarDu,
			"Daglig reise reisevei"
		),
		harDuEnReiseveiPaSeksKilometerEllerMer = harDuReiseveiPaMerEnn6Km, // JA|NEI
		harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde =
		if (harDuReiseveiPaMerEnn6Km.equals("nei", true))
		// JA | NEI,
			validateNotNull(
				tilleggsstonad.harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde,
				"Har du av medisinske årsaker behov for transport uavhengig av reisens lengde"
			)
		else null,
		velgLand1 = tilleggsstonad.velgLand1 ?: VelgLand(label = "Norge", "NO"),
		adresse1 = validateNotNull(tilleggsstonad.adresse1, "Daglig reise adresse"),
		postnr1 = tilleggsstonad.postnr1,
		poststed = tilleggsstonad.poststed,
		postkode = tilleggsstonad.postkode,
		kanDuReiseKollektivtDagligReise = validateNotNull(
			tilleggsstonad.kanDuReiseKollektivtDagligReise,
			"Daglig reise kan du reise kollektivt"
		), // ja | nei
		hvilkeUtgifterHarDuIForbindelseMedReisenDagligReise = tilleggsstonad.hvilkeUtgifterHarDuIForbindelseMedReisenDagligReise, // Hvis kanDuReiseKollektivtDagligReise == ja
		hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt = tilleggsstonad.hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt,
		kanIkkeReiseKollektivtDagligReise = tilleggsstonad.kanIkkeReiseKollektivtDagligReise
	)
}

private fun convertToJsonReiseSamling(tilleggsstonad: Application): JsonReiseSamling {
	return JsonReiseSamling(
		startOgSluttdatoForSamlingene = validateNotNull(
			tilleggsstonad.startOgSluttdatoForSamlingene,
			"Reise til samling start- og sluttdato mangler"
		),
		hvorLangReiseveiHarDu1 = tilleggsstonad.hvorLangReiseveiHarDu1,
		velgLandReiseTilSamling = validateNotNull(
			tilleggsstonad.velgLandReiseTilSamling,
			"Reise til samling - mangler land"
		),
		adresse2 = validateNotNull(tilleggsstonad.adresse2, "Reise til samling - mangler adresse"),
		postnr2 = tilleggsstonad.postnr2,
		poststed = tilleggsstonad.poststed,
		postkode = tilleggsstonad.postkode,
		kanDuReiseKollektivtReiseTilSamling = validateNotNull(
			tilleggsstonad.kanDuReiseKollektivtReiseTilSamling,
			"Reise til samling - mangler svar kan du reise kollektivt"
		),
		kanReiseKollektivt = tilleggsstonad.kanReiseKollektivt,
		kanIkkeReiseKollektivtReiseTilSamling = tilleggsstonad.kanIkkeReiseKollektivtReiseTilSamling,
		bekreftelseForAlleSamlingeneDuSkalDeltaPa = tilleggsstonad.bekreftelseForAlleSamlingeneDuSkalDeltaPa
	)
}

private fun convertToJsonReiseArbeidssoker(tilleggsstonad: Application): JsonDagligReiseArbeidssoker {
	return JsonDagligReiseArbeidssoker(
		reisedatoDdMmAaaa = validateNotNull(
			tilleggsstonad.reiseDato,
			"Reise arbeidssøker - reisetidspunkt mangler"
		),
		hvorforReiserDuArbeidssoker = validateNotNull(
			tilleggsstonad.hvorforReiserDuArbeidssoker,
			"Reise arbeidssøker - hvorfor reiser du svar mangler"
		),
		dekkerAndreEnnNavEllerDegSelvReisenHeltEllerDelvis = validateNotNull(
			tilleggsstonad.dekkerAndreEnnNavEllerDegSelvReisenHeltEllerDelvis,
			"Reise arbeissøker - dekker andre reisen svar mangler"
		),// JA|NEI
		mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene = validateNotNull(
			tilleggsstonad.mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene,
			"Reise arbeidssøker -  mottatt dagpenger svar mangler"
		), // JA|NEI
		harMottattDagpengerSiste6Maneder = tilleggsstonad.harMottattDagpengerSiste6Maneder,
		hvorLangReiseveiHarDu3 = validateNotNull(
			tilleggsstonad.hvorLangReiseveiHarDu3,
			"Daglig reise reisevei"
		),
		velgLandArbeidssoker = tilleggsstonad.velgLandArbeidssoker ?: VelgLand(label = "Norge", "NO"),
		adresse = validateNotNull(tilleggsstonad.adresse, "Reise arbeidssøker -  adresse mangler"),
		postnr = tilleggsstonad.postnr,
		poststed = tilleggsstonad.poststed,
		postkode = tilleggsstonad.postkode, // Postkode benyttes dersom land != Norge

		kanDuReiseKollektivtArbeidssoker = validateNotNull(
			tilleggsstonad.kanDuReiseKollektivtArbeidssoker,
			"Reise arbeidssøker - kan du reise kollektivt mangler"
		), // ja | nei
		hvilkeUtgifterHarDuIForbindelseMedReisen3 = tilleggsstonad.hvilkeUtgifterHarDuIForbindelseMedReisen3, // Hvis kanDuReiseKollektivtDagligReise == ja
		kanIkkeReiseKollektivtArbeidssoker = tilleggsstonad.kanIkkeReiseKollektivtArbeidssoker
	)
}


private fun convertToJsonOppstartOgAvsluttetAktivitet(tilleggsstonad: Application): JsonOppstartOgAvsluttetAktivitet {
	return JsonOppstartOgAvsluttetAktivitet(
		startdatoDdMmAaaa1 = validateNotNull(
			tilleggsstonad.soknadsPeriode?.startdato ?: tilleggsstonad.startdato,
			"Oppstart og avslutning av aktivitet - reisetidspunkt mangler"
		),
		sluttdatoDdMmAaaa1 = validateNotNull(
			tilleggsstonad.soknadsPeriode?.sluttdato ?: tilleggsstonad.sluttdato,
			"Oppstart og avslutning av aktivitet - reisetidspunkt mangler"
		),
		hvorLangReiseveiHarDu2 = validateNotNull(
			tilleggsstonad.hvorLangReiseveiHarDu2,
			"Oppstart og avslutning av aktivitet - reiseveilengde svar mangler"
		),
		hvorMangeGangerSkalDuReiseEnVei = validateNotNull(
			tilleggsstonad.hvorMangeGangerSkalDuReiseEnVei,
			"Oppstart og avslutning av aktivitet - antall reiser svar mangler"
		),
		velgLand3 = tilleggsstonad.velgLand3 ?: VelgLand(label = "Norge", "NO"),
		adresse3 = validateNotNull(tilleggsstonad.adresse3, "Oppstart og avslutning av aktivitet -  adresse mangler"),
		postnr3 = tilleggsstonad.postnr3,
		poststed = tilleggsstonad.poststed,
		postkode = tilleggsstonad.postkode,
		harDuBarnSomSkalFlytteMedDeg = validateNotNull(
			tilleggsstonad.harDuBarnSomSkalFlytteMedDeg,
			"Oppstart og avslutning av aktivitet - har du barn som skal flytte med deg svar mangler"
		),
		barnSomSkalFlytteMedDeg = tilleggsstonad.barnSomSkalFlytteMedDeg,
		harDuBarnSomBorHjemmeOgSomIkkeErFerdigMedFjerdeSkolear = tilleggsstonad.harDuBarnSomBorHjemmeOgSomIkkeErFerdigMedFjerdeSkolear,
		harDuSaerligBehovForFlereHjemreiserEnnNevntOvenfor = validateNotNull(
			tilleggsstonad.harDuSaerligBehovForFlereHjemreiserEnnNevntOvenfor,
			"Oppstart og avslutning av aktivitet - Særlige behov svar mangler"
		),
		bekreftelseForBehovForFlereHjemreiser1 = tilleggsstonad.bekreftelseForBehovForFlereHjemreiser1,
		kanDuReiseKollektivtOppstartAvslutningHjemreise = validateNotNull(
			tilleggsstonad.kanDuReiseKollektivtOppstartAvslutningHjemreise,
			"Oppstart og avslutning av aktivitet - kan du reise kollektivt svar mangler"
		),
		hvilkeUtgifterHarDuIForbindelseMedReisen4 = tilleggsstonad.hvilkeUtgifterHarDuIForbindelseMedReisen4,
		kanIkkeReiseKollektivtOppstartAvslutningHjemreise = tilleggsstonad.kanIkkeReiseKollektivtOppstartAvslutningHjemreise
	)
}

private fun <T : Any> validateNotNull(input: T?, field: String): T {
	return input ?: throw IllegalActionException("Mangler input for $field")
}
