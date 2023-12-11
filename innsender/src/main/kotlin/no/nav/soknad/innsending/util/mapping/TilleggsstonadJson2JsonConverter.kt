package no.nav.soknad.innsending.util.mapping

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.DokumentSoknadDto


fun convertToJson(soknadDto: DokumentSoknadDto, json: ByteArray?): JsonApplication {
	if (json == null || json.isEmpty())
		throw BackendErrorException("${soknadDto.innsendingsId}: json fil av søknaden mangler")

	val mapper = jacksonObjectMapper()
	//mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
	val json = mapper.readValue(json, Root::class.java)

	return JsonApplication(
		timezone = json.data.metadata.timezone,
		language = json.language,
		personInfo = JsonPersonInfo(
			fornavn = json.data.data.fornavnSoker,
			etternavn = json.data.data.etternavnSoker,
			ident = PersonIdent(ident = json.data.data.fodselsnummerDnummerSoker, identType = IdentType.PERSONNR)
		),
		tilleggsstonad = convertToJsonTilleggsstonad(json.data.data)
	)
}

fun convertToJsonTilleggsstonad(tilleggsstonad: Application): JsonTilleggsstonad {
	return JsonTilleggsstonad(
		aktivitetsinformasjon = JsonAktivitetsInformasjon("12345"), // TODO
		maalgruppeinformasjon = convertToJsonMaalgruppeinformasjon(tilleggsstonad),
		rettighetstype = convertToJsonRettighetstyper(tilleggsstonad)

	)
}

private fun convertToJsonMaalgruppeinformasjon(tilleggsstonad: Application): JsonMaalgruppeinformasjon { // TODO
	return JsonMaalgruppeinformasjon(
		periode = AktivitetsPeriode(startdatoDdMmAaaa = "01-01-2024", sluttdatoDdMmAaaa = "30-06-2024"),
		kilde = "BRUKERREGISTRERT",
		maalgruppetype = JsonMaalgruppetyper(value = "ENSFORARBS")
	)
}

private fun convertToJsonRettighetstyper(tilleggsstonad: Application): JsonRettighetstyper {
	return JsonRettighetstyper(
		reise = convertToReisestottesoknad(tilleggsstonad),
		tilsynsutgifter = convertToTilsynsutgifter(tilleggsstonad),
		laeremiddelutgifter = convertToLaeremiddelutgifter(tilleggsstonad),
		bostotte = convertToJsonBostotte(tilleggsstonad),
		flytteutgifter = convertToJsonFlytteutgifter(tilleggsstonad)
	)
}

private fun convertToJsonFlytteutgifter(tilleggsstonad: Application): JsonFlytteutgifter? {
	if (tilleggsstonad.hvorforFlytterDu == null || tilleggsstonad.ikkeRegistrertAktivitetsperiode == null
		|| tilleggsstonad.oppgiForsteDagINyJobbDdMmAaaa == null
		|| tilleggsstonad.narFlytterDuDdMmAaaa == null
		|| tilleggsstonad.farDuDekketUtgifteneDineTilFlyttingPaAnnenMateEnnMedStonadFraNav == null
		|| tilleggsstonad.ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra == null
	) return null

	return JsonFlytteutgifter(
		aktivitetsperiode = tilleggsstonad.ikkeRegistrertAktivitetsperiode,
		hvorforFlytterDu = tilleggsstonad.hvorforFlytterDu,
		narFlytterDuDdMmAaaa = tilleggsstonad.narFlytterDuDdMmAaaa,
		oppgiForsteDagINyJobbDdMmAaaa = tilleggsstonad.oppgiForsteDagINyJobbDdMmAaaa,
		erBostedEtterFlytting = tilleggsstonad.detteErAdressenJegSkalBoPaEtterAtJegHarFlyttet != null,
		velgLand1 = tilleggsstonad.velgLand1 ?: VelgLand1(label = "NOR", "Norge"),
		adresse1 = validateNoneNull(tilleggsstonad.adresse1, "Daglig reise adresse"),
		postnr1 = validateNoneNull(tilleggsstonad.postnr1, "Daglig reise postnummer"),
		farDuDekketUtgifteneDineTilFlyttingPaAnnenMateEnnMedStonadFraNav = tilleggsstonad.farDuDekketUtgifteneDineTilFlyttingPaAnnenMateEnnMedStonadFraNav,
		ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra = tilleggsstonad.ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra,
		jegFlytterSelv = tilleggsstonad.jegFlytterSelv,
		jegVilBrukeFlyttebyra = tilleggsstonad.jegVilBrukeFlyttebyra,
		jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv = tilleggsstonad.jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv
	)
}

private fun convertToJsonBostotte(tilleggsstonad: Application): JsonBostottesoknad? {
	if (tilleggsstonad.hvilkeBoutgifterSokerDuOmAFaDekket == null || tilleggsstonad.ikkeRegistrertAktivitetsperiode == null) return null

	return JsonBostottesoknad(
		aktivitetsperiode = tilleggsstonad.ikkeRegistrertAktivitetsperiode,
		hvilkeBoutgifterSokerDuOmAFaDekket = tilleggsstonad.hvilkeBoutgifterSokerDuOmAFaDekket,
		bostotteIForbindelseMedSamling = tilleggsstonad.bostotteIForbindelseMedSamling,
		mottarDuBostotteFraKommunen = tilleggsstonad.mottarDuBostotteFraKommunen ?: "Nei", // "Ja" | "Nei"
		hvilkeAdresserHarDuBoutgifterPa = tilleggsstonad.hvilkeAdresserHarDuBoutgifterPa,
		boutgifterPaAktivitetsadressen = tilleggsstonad.boutgifterPaAktivitetsadressen,
		boutgifterJegHarHattPaHjemstedetMittMenSomHarOpphortIForbindelseMedAktiviteten = tilleggsstonad.boutgifterJegHarHattPaHjemstedetMittMenSomHarOpphortIForbindelseMedAktiviteten,
		erDetMedisinskeForholdSomPavirkerUtgifteneDinePaAktivitetsstedet = tilleggsstonad.erDetMedisinskeForholdSomPavirkerUtgifteneDinePaAktivitetsstedet
	)
}

private fun convertToLaeremiddelutgifter(tilleggsstonad: Application): JsonLaeremiddelutgifter? {
	if (tilleggsstonad.hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore == null || tilleggsstonad.ikkeRegistrertAktivitetsperiode == null) return null
	return JsonLaeremiddelutgifter(
		aktivitetsperiode = tilleggsstonad.ikkeRegistrertAktivitetsperiode,
		hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore = tilleggsstonad.hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore,
		hvilketKursEllerAnnenFormForUtdanningSkalDuTa = tilleggsstonad.hvilketKursEllerAnnenFormForUtdanningSkalDuTa,
		oppgiHvorMangeProsentDuStudererEllerGarPaKurs = tilleggsstonad.oppgiHvorMangeProsentDuStudererEllerGarPaKurs ?: 0,
		harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler = tilleggsstonad.harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler
			?: "Nei",
		utgifterTilLaeremidler = tilleggsstonad.utgifterTilLaeremidler ?: 0,
		farDuDekketLaeremidlerEtterAndreOrdninger = tilleggsstonad.farDuDekketLaeremidlerEtterAndreOrdninger ?: "Nei",
		hvorMyeFarDuDekketAvEnAnnenAktor = tilleggsstonad.hvorMyeFarDuDekketAvEnAnnenAktor,
		hvorStortBelopSokerDuOmAFaDekketAvNav = tilleggsstonad.hvorStortBelopSokerDuOmAFaDekketAvNav ?: 0
	)
}

private fun convertToTilsynsutgifter(tilleggsstonad: Application): JsonTilsynsutgifter? {
	if (tilleggsstonad.datagrid == null || tilleggsstonad.ikkeRegistrertAktivitetsperiode == null) return null
	return JsonTilsynsutgifter(
		aktivitetsPeriode = AktivitetsPeriode(
			tilleggsstonad.ikkeRegistrertAktivitetsperiode.startdatoDdMmAaaa,
			tilleggsstonad.ikkeRegistrertAktivitetsperiode.sluttdatoDdMmAaaa
		),
		barnePass = tilleggsstonad.datagrid.map {
			BarnePass(
				fornavn = it.fornavn, etternavn = it.etternavn, fodselsdatoDdMmAaaa = it.fodselsdatoDdMmAaaa,
				jegSokerOmStonadTilPassAvDetteBarnet = it.jegSokerOmStonadTilPassAvDetteBarnet,
				sokerStonadForDetteBarnet = it.sokerStonadForDetteBarnet,

				)
		},
		fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa = tilleggsstonad.fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa
	)
}

private fun convertToReisestottesoknad(tilleggsstonad: Application): JsonReisestottesoknad? {
	if (tilleggsstonad.hvorforReiserDu == null) return null
	return JsonReisestottesoknad(
		hvorforReiserDu = tilleggsstonad.hvorforReiserDu,
		dagligReise = if (tilleggsstonad.hvorforReiserDu.dagligReise == true) convertToJsonDagligReise(tilleggsstonad) else null,
		reiseSamling = if (tilleggsstonad.hvorforReiserDu.reiseTilSamling == true) convertToJsonReiseSamling(tilleggsstonad) else null,
		dagligReiseArbeidssoker = if (tilleggsstonad.hvorforReiserDu.reiseNarDuErArbeidssoker == true) convertToJsonReise_Arbeidssoker(
			tilleggsstonad
		) else null,
		oppstartOgAvsluttetAktivitet = if (tilleggsstonad.hvorforReiserDu.reisePaGrunnAvOppstartAvslutningEllerHjemreise == true) convertToJsonOppstartOgAvsluttetAktivitet(
			tilleggsstonad
		) else null
	)
}


private fun convertToJsonDagligReise(tilleggsstonad: Application): JsonDagligReise {
	return JsonDagligReise(
		startdatoDdMmAaaa = tilleggsstonad.startdatoDdMmAaaa,
		sluttdatoDdMmAaaa = tilleggsstonad.sluttdatoDdMmAaaa,
		hvorMangeReisedagerHarDuPerUke = tilleggsstonad.hvorMangeReisedagerHarDuPerUke,
		harDuEnReiseveiPaSeksKilometerEllerMer = tilleggsstonad.harDuEnReiseveiPaSeksKilometerEllerMer, // JA|NEI
		harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde = tilleggsstonad.harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde, // JA | NEI,
		hvorLangReiseveiHarDu = validateNoneNull(
			tilleggsstonad.hvorLangReiseveiHarDu?.toString(),
			"Daglig reise reisevei"
		).toInt(),
		velgLand1 = tilleggsstonad.velgLand1 ?: VelgLand1(label = "NOR", "Norge"),
		adresse1 = validateNoneNull(tilleggsstonad.adresse1, "Daglig reise adresse"),
		postnr1 = validateNoneNull(tilleggsstonad.postnr1, "Daglig reise postnummer"),
		kanDuReiseKollektivtDagligReise = validateNoneNull(
			tilleggsstonad.kanDuReiseKollektivtDagligReise,
			"Daglig reise kan du reise kollektivt"
		), // ja | nei
		hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise = tilleggsstonad.hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise, // Hvis kanDuReiseKollektivtDagligReise == ja
		hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt = tilleggsstonad.hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt,
		kanIkkeReiseKollektivtDagligReise = tilleggsstonad.kanIkkeReiseKollektivtDagligReise
	)
}

private fun convertToJsonReiseSamling(tilleggsstonad: Application): JsonReiseSamling {
	return JsonReiseSamling(
		startOgSluttdatoForSamlingene = validateNoneNull(
			tilleggsstonad.startOgSluttdatoForSamlingene,
			"Reise til samling start- og sluttdato mangler"
		),
		hvorLangReiseveiHarDu1 = tilleggsstonad.hvorLangReiseveiHarDu1,
		velgLandReiseTilSamling = validateNoneNull(
			tilleggsstonad.velgLandReiseTilSamling,
			"Reise til samling - mangler land"
		),
		adresse2 = validateNoneNull(tilleggsstonad.adresse2, "Reise til samling - mangler adresse"),
		postnr2 = validateNoneNull(tilleggsstonad.postnr2, "Reise til samling -mangler postnummer"),
		kanDuReiseKollektivtReiseTilSamling = validateNoneNull(
			tilleggsstonad.kanDuReiseKollektivtReiseTilSamling,
			"Reise til samling - mangler svar kan du reise kollektivt"
		),
		kanReiseKollektivt = tilleggsstonad.kanReiseKollektivt,
		kanIkkeReiseKollektivtReiseTilSamling = tilleggsstonad.kanIkkeReiseKollektivtReiseTilSamling,
		bekreftelseForAlleSamlingeneDuSkalDeltaPa = tilleggsstonad.bekreftelseForAlleSamlingeneDuSkalDeltaPa
	)
}

private fun convertToJsonReise_Arbeidssoker(tilleggsstonad: Application): JsonDagligReiseArbeidssoker {
	return JsonDagligReiseArbeidssoker(
		reisedatoDdMmAaaa = validateNoneNull(
			tilleggsstonad.reisedatoDdMmAaaa,
			"Reise arbeidssøker - reisetidspunkt mangler"
		),
		hvorforReiserDuArbeidssoker = validateNoneNull(
			tilleggsstonad.hvorforReiserDuArbeidssoker,
			"Reise arbeidssøker - hvorfor reiser du svar mangler"
		),
		dekkerAndreEnnNavEllerDegSelvReisenHeltEllerDelvis = validateNoneNull(
			tilleggsstonad.dekkerAndreEnnNavEllerDegSelvReisenHeltEllerDelvis,
			"Reise arbeissøker - dekker andre reisen svar mangler"
		),// JA|NEI
		mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene = validateNoneNull(
			tilleggsstonad.mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene,
			"Reise arbeidssøker -  mottatt dagpenger svar mangler"
		), // JA|NEI
		harMottattDagpengerSiste6Maneder = tilleggsstonad.harMottattDagpengerSiste6Maneder,
		hvorLangReiseveiHarDu3 = validateNoneNull(
			tilleggsstonad.hvorLangReiseveiHarDu3,
			"Daglig reise reisevei"
		).toInt(),
		velgLandArbeidssoker = tilleggsstonad.velgLandArbeidssoker ?: VelgLandArbeidssoker(label = "NOR", "Norge"),
		adresse = validateNoneNull(tilleggsstonad.adresse, "Reise arbeidssøker -  adresse mangler"),
		postnr = validateNoneNull(tilleggsstonad.postnr, "Reise arbeidssøker -  postnummer mangler"),
		kanDuReiseKollektivtArbeidssoker = validateNoneNull(
			tilleggsstonad.kanDuReiseKollektivtArbeidssoker,
			"Reise arbeidssøker - kan du reise kollektivt mangler"
		), // ja | nei
		hvilkeUtgifterHarDuIForbindelseMedReisen3 = tilleggsstonad.hvilkeUtgifterHarDuIForbindelseMedReisen3, // Hvis kanDuReiseKollektivtDagligReise == ja
		kanIkkeReiseKollektivtArbeidssoker = tilleggsstonad.kanIkkeReiseKollektivtArbeidssoker
	)
}


private fun convertToJsonOppstartOgAvsluttetAktivitet(tilleggsstonad: Application): JsonOppstartOgAvsluttetAktivitet {
	return JsonOppstartOgAvsluttetAktivitet(
		startdatoDdMmAaaa1 = validateNoneNull(
			tilleggsstonad.startdatoDdMmAaaa1,
			"Oppstart og avslutning av aktivitet - reisetidspunkt mangler"
		),
		sluttdatoDdMmAaaa1 = validateNoneNull(
			tilleggsstonad.sluttdatoDdMmAaaa1,
			"Oppstart og avslutning av aktivitet - reisetidspunkt mangler"
		),
		hvorLangReiseveiHarDu2 = validateNoneNull(
			tilleggsstonad.hvorLangReiseveiHarDu2,
			"Oppstart og avslutning av aktivitet - reiseveilengde svar mangler"
		).toInt(),
		hvorMangeGangerSkalDuReiseEnVei = validateNoneNull(
			tilleggsstonad.hvorMangeGangerSkalDuReiseEnVei,
			"Oppstart og avslutning av aktivitet - antall reiser svar mangler"
		).toInt(),
		velgLand3 = tilleggsstonad.velgLand3 ?: VelgLand3(label = "NOR", "Norge"),
		adresse3 = validateNoneNull(tilleggsstonad.adresse3, "Oppstart og avslutning av aktivitet -  adresse mangler"),
		postnr3 = validateNoneNull(tilleggsstonad.postnr3, "Oppstart og avslutning av aktivitet -  postnummer mangler"),
		harDuBarnSomSkalFlytteMedDeg = validateNoneNull(
			tilleggsstonad.harDuBarnSomSkalFlytteMedDeg,
			"Oppstart og avslutning av aktivitet - har du barn som skal flytte med deg svar mangler"
		),
		barnSomSkalFlytteMedDeg = tilleggsstonad.barnSomSkalFlytteMedDeg,
		harDuBarnSomBorHjemmeOgSomIkkeErFerdigMedFjerdeSkolear = tilleggsstonad.harDuBarnSomBorHjemmeOgSomIkkeErFerdigMedFjerdeSkolear,
		harDuSaerligBehovForFlereHjemreiserEnnNevntOvenfor = validateNoneNull(
			tilleggsstonad.harDuSaerligBehovForFlereHjemreiserEnnNevntOvenfor,
			"Oppstart og avslutning av aktivitet - Særlige behov svar mangler"
		),
		bekreftelseForBehovForFlereHjemreiser1 = tilleggsstonad.bekreftelseForBehovForFlereHjemreiser1,
		kanDuReiseKollektivtOppstartAvslutningHjemreise = validateNoneNull(
			tilleggsstonad.kanDuReiseKollektivtOppstartAvslutningHjemreise,
			"Oppstart og avslutning av aktivitet - kan du reise kollektivt svar mangler"
		),
		hvilkeUtgifterHarDuIForbindelseMedReisen4 = tilleggsstonad.hvilkeUtgifterHarDuIForbindelseMedReisen4,
		kanIkkeReiseKollektivtOppstartAvslutningHjemreise = tilleggsstonad.kanIkkeReiseKollektivtOppstartAvslutningHjemreise
	)
}

private fun <T : Any> validateNoneNull(input: T?, field: String): T {
	return input ?: throw IllegalActionException("Mangler input for $field")
}
