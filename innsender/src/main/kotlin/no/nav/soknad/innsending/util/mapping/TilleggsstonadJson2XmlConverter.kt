package no.nav.soknad.innsending.util.mapping

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule
import no.nav.soknad.innsending.model.DokumentSoknadDto
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*


fun json2Xml(soknadDto: DokumentSoknadDto, jsonFil: ByteArray?): ByteArray {
	// Konverter json-string til tilleggsstonadJson
	val tilleggstonadJsonObj = convertToJson(soknadDto, jsonFil)

	// Map tilleggsstonadFraJson til tilleggsstonadXML
	val tilleggsstonadXmlObj = convertToTilleggsstonadsskjema(soknadDto, tilleggstonadJsonObj)

	// Konverter tilleggsstonadXML til xml-string
	return convertToXml(tilleggsstonadXmlObj)
}

fun json2Xml(soknadDto: DokumentSoknadDto, tilleggstonadJsonObj: JsonApplication): ByteArray {

	// Map tilleggsstonadFraJson til tilleggsstonadXML
	val tilleggsstonadXmlObj = convertToTilleggsstonadsskjema(soknadDto, tilleggstonadJsonObj)

	// Konverter tilleggsstonadXML til xml-bytearray
	return convertToXml(tilleggsstonadXmlObj)
}

fun convertToXml(tilleggsstonad: Tilleggsstoenadsskjema): ByteArray {
	val xmlMapper = XmlMapper(
		JacksonXmlModule().apply {
			setDefaultUseWrapper(false)
		}
	).apply {
		enable(SerializationFeature.INDENT_OUTPUT)
		disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
	}
	xmlMapper.setDateFormat(SimpleDateFormat("yyyy-MM-ddXXX"))
	xmlMapper.registerModule(JaxbAnnotationModule())
	val xml = xmlMapper.writeValueAsString(tilleggsstonad)
	return xml.toByteArray()
}

fun convertToTilleggsstonadsskjema(
	soknadDto: DokumentSoknadDto,
	tilleggstonadJsonObj: JsonApplication
): Tilleggsstoenadsskjema {
	return Tilleggsstoenadsskjema(
		aktivitetsinformasjon = convertToAktivitetsinformasjon(tilleggstonadJsonObj),
		personidentifikator = soknadDto.brukerId,
		maalgruppeinformasjon = convertToMaalgruppeinformasjon(tilleggstonadJsonObj),
		rettighetstype = convertToRettighetstype(soknadDto, tilleggstonadJsonObj)
	)

}

fun convertToAktivitetsinformasjon(tilleggstonadJsonObj: JsonApplication): Aktivitetsinformasjon {
	return Aktivitetsinformasjon(tilleggstonadJsonObj.tilleggsstonad.aktivitetsinformasjon?.aktivitet) // TODO
}

fun convertToMaalgruppeinformasjon(tilleggstonadJsonObj: JsonApplication): Maalgruppeinformasjon {
	return Maalgruppeinformasjon(
		periode = Periode(
			fom = convertToDateStringWithTimeZone("2023-12-01"), tom = convertToDateStringWithTimeZone("2024-01-31")
		),
		kilde = "BRUKERREGISTRERT",
		maalgruppetype = Maalgruppetyper(value = "NEDSARBEVN", kodeverksRef = "NEDSARBEVN")
	) // TODO
}

private fun convertToDateStringWithTimeZone(date: String): String {
	val inputFormat = SimpleDateFormat("yyyy-MM-dd")
	val inputDate = inputFormat.parse(date.substring(0, 9))
	val outputFormat = SimpleDateFormat("yyyy-MM-ddXXX", Locale.getDefault())
	return outputFormat.format(inputDate)
}

fun convertToRettighetstype(soknadDto: DokumentSoknadDto, tilleggstonadJsonObj: JsonApplication): Rettighetstype {
	return Rettighetstype(
		boutgifter = convertBostotte(tilleggstonadJsonObj),
		laeremiddelutgifter = convertLaremiddler(tilleggstonadJsonObj),
		flytteutgifter = convertFlytteutgifter(tilleggstonadJsonObj),
		tilsynsutgifter = convertTilsynsutgifter(tilleggstonadJsonObj),
		reiseutgifter = convertReiseUtgifter(tilleggstonadJsonObj)
	)
}

fun convertBostotte(tilleggstonadJsonObj: JsonApplication): Boutgifter? {
	if (tilleggstonadJsonObj.tilleggsstonad.rettighetstype?.bostotte == null ||
		tilleggstonadJsonObj.tilleggsstonad.rettighetstype.bostotte.aktivitetsperiode == null
	) return null

	val bostottesoknad = tilleggstonadJsonObj.tilleggsstonad.rettighetstype.bostotte
	return Boutgifter(
		periode = Periode(
			fom = convertToDateStringWithTimeZone(bostottesoknad.aktivitetsperiode.startdatoDdMmAaaa),
			tom = convertToDateStringWithTimeZone(bostottesoknad.aktivitetsperiode.sluttdatoDdMmAaaa)
		),
		harFasteBoutgifter = true,
		harBoutgifterVedSamling = false,
		boutgifterHjemstedAktuell = 8000,
		boutgifterPgaFunksjonshemminger = null,
		mottarBostoette = false,
		samlingsperiode = null
	)

}

fun convertLaremiddler(tilleggstonadJsonObj: JsonApplication): Laeremiddelutgifter? {
	if (tilleggstonadJsonObj.tilleggsstonad.rettighetstype?.laeremiddelutgifter == null) return null

	val laeremiddelutgifter = tilleggstonadJsonObj.tilleggsstonad.rettighetstype.laeremiddelutgifter

	return Laeremiddelutgifter(
		periode = convertPeriode(
			fom = laeremiddelutgifter.aktivitetsperiode.startdatoDdMmAaaa,
			tom = laeremiddelutgifter.aktivitetsperiode.sluttdatoDdMmAaaa
		),
		hvorMyeDekkesAvAnnenAktoer = laeremiddelutgifter.hvorMyeFarDuDekketAvEnAnnenAktor?.toDouble(),
		hvorMyeDekkesAvNAV = laeremiddelutgifter.hvorStortBelopSokerDuOmAFaDekketAvNav.toDouble(),
		skolenivaa = Skolenivaaer(value = "HGU"), // TODO mapping av laeremiddelutgifter.hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore
		prosentandelForUtdanning = laeremiddelutgifter.oppgiHvorMangeProsentDuStudererEllerGarPaKurs,
		beloep = laeremiddelutgifter.utgifterTilLaeremidler,
		erUtgifterDekket = ErUtgifterDekket("Nei")   // TODO mapping av laeremiddelutgifter.farDuDekketLaeremidlerEtterAndreOrdninger
	)
}

fun convertFlytteutgifter(tilleggstonadJsonObj: JsonApplication): Flytteutgifter? {
	if (tilleggstonadJsonObj.tilleggsstonad.rettighetstype?.flytteutgifter == null) return null

	val flytteutgifter = tilleggstonadJsonObj.tilleggsstonad.rettighetstype.flytteutgifter

	return Flytteutgifter(
		flyttingPgaAktivitet = "Jeg flytter i forbindelse med at jeg skal gjennomføre en aktivitet".equals(
			flytteutgifter.hvorforFlytterDu,
			true
		),
		erUtgifterTilFlyttingDekketAvAndreEnnNAV = convertToBoolean(flytteutgifter.farDuDekketUtgifteneDineTilFlyttingPaAnnenMateEnnMedStonadFraNav)
			?: false,
		flytterSelv = (flytteutgifter.jegFlytterSelv != null || flytteutgifter.jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv != null).toString(),
		flyttingPgaNyStilling = "Jeg flytter fordi jeg har fått ny jobb".equals(flytteutgifter.hvorforFlytterDu, true),
		flyttedato = convertToDateStringWithTimeZone(flytteutgifter.narFlytterDuDdMmAaaa),
		tilflyttingsadresse = SammensattAdresse( // TODO mangler adresse angivelse i skjema
			land = flytteutgifter.velgLand1.value,
			adresse = flytteutgifter.adresse1,
			postnr = flytteutgifter.postnr1
		).sammensattAdresse,
		avstand = convertFlytteAvstand(flytteutgifter), // TODO sjekk avstand i skjema; avstanden er uavhengig av hvordan flytting skjer
		sumTilleggsutgifter = convertFlytteutgifter(flytteutgifter).toDouble()
	)

}

fun convertFlytteAvstand(flytteutgifter: JsonFlytteutgifter): Int {
	return if (flytteutgifter.jegFlytterSelv != null) {
		flytteutgifter.jegFlytterSelv.hvorLangtSkalDuFlytte
	} else if (flytteutgifter.jegVilBrukeFlyttebyra != null) {
		0
	} else if (flytteutgifter.jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv != null) {
		flytteutgifter.jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv.hvorLangtSkalDuFlytte1
	} else {
		return 0
	}

}

fun convertFlytteutgifter(flytteutgifter: JsonFlytteutgifter): Int {
	if (flytteutgifter.jegFlytterSelv != null) {
		val flytterSelv = flytteutgifter.jegFlytterSelv
		return (flytterSelv.bom ?: 0) + (flytterSelv.annet ?: 0) + (flytterSelv.hengerleie ?: 0) + (flytterSelv.parkering
			?: 0) + (flytterSelv.ferje ?: 0)
	} else if (flytteutgifter.jegVilBrukeFlyttebyra != null) {
		val flytteByra = flytteutgifter.jegVilBrukeFlyttebyra
		return when {
			"Flyttebyrå 1".equals(flytteByra.jegVelgerABruke, true) -> flytteByra.belop
			else -> flytteByra.belop1
		}
	} else if (flytteutgifter.jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv != null) {
		val flytterSelv = flytteutgifter.jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv
		return (flytterSelv.bom ?: 0) + (flytterSelv.annet ?: 0) + (flytterSelv.hengerleie ?: 0) + (flytterSelv.parkering
			?: 0) + (flytterSelv.ferje ?: 0)
	} else {
		return 0
	}
}

fun convertTilsynsutgifter(tilleggstonadJsonObj: JsonApplication): Tilsynsutgifter? {
	if (!hasTilsynsutgifter(tilleggstonadJsonObj)) return null

	val utgifterBarn = convertTilsynsutgifterBarn(tilleggstonadJsonObj)
	val utgifterFamilie = convertFamilieutgifter(tilleggstonadJsonObj) // TODO er denne relevant?

	if (utgifterBarn == null && utgifterFamilie == null) return null

	return Tilsynsutgifter(tilsynsutgifterBarn = utgifterBarn, tilynsutgifterFamilie = utgifterFamilie)
}

private fun hasTilsynsutgifter(tilleggstonadJsonObj: JsonApplication): Boolean {
	return tilleggstonadJsonObj.tilleggsstonad.rettighetstype?.tilsynsutgifter != null
}

private fun convertTilsynsutgifterBarn(tilleggstonadJsonObj: JsonApplication): TilsynsutgifterBarn? {
	if (tilleggstonadJsonObj.tilleggsstonad.rettighetstype?.tilsynsutgifter == null) return null

	val tilsynsutgifter = tilleggstonadJsonObj.tilleggsstonad.rettighetstype.tilsynsutgifter
	return TilsynsutgifterBarn(
		Periode(
			fom = convertToDateStringWithTimeZone(tilsynsutgifter.aktivitetsPeriode.startdatoDdMmAaaa),
			tom = convertToDateStringWithTimeZone(tilsynsutgifter.aktivitetsPeriode.sluttdatoDdMmAaaa)
		),
		barn = tilsynsutgifter.barnePass.filter { convertToBoolean(it.jegSokerOmStonadTilPassAvDetteBarnet) ?: false }.map {
			Barn(
				personidentifikator = stripAndFormatToDDMMYY(it.fodselsdatoDdMmAaaa),
				tilsynskategori = Tilsynskategorier("KOM"), // TODO mapping av it.sokerStonadForDetteBarnet.hvemPasserBarnet
				navn = it.fornavn + " " + it.etternavn,
				harFullfoertFjerdeSkoleaar = convertToBoolean(it.sokerStonadForDetteBarnet?.harBarnetFullfortFjerdeSkolear)
					?: false,
				maanedligUtgiftTilsynBarn = it.sokerStonadForDetteBarnet?.oppgiManedligUtgiftTilBarnepass ?: 0
			)
		},
		annenForsoergerperson = tilsynsutgifter.fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa
	)
}

private fun stripAndFormatToDDMMYY(date: String): String {
	// Anta YYYY-MM-DD
	val localDate = LocalDate.parse(date)
	val dtf = DateTimeFormatter.ofPattern("ddMMyy")

	return dtf.format(localDate)
}

private fun convertFamilieutgifter(tilleggstonadJsonObj: JsonApplication): TilsynsutgifterFamilie? {
	if (!hasTilsynsutgifterFamilie(tilleggstonadJsonObj)) return null


	// TODO erstatt eksempel nedenfor
	return TilsynsutgifterFamilie(
		Periode(
			fom = convertToDateStringWithTimeZone("2023-12-01"), // TODO
			tom = convertToDateStringWithTimeZone("2024-03-30")  // TODO
		),
		deltTilsyn = true,
		annenTilsynsperson = "Annen Person",
		tilsynForetasAv = "Annen Person",
		tilsynsmottaker = "Rart Barn",
		maanedligUtgiftTilsynFam = 3500
	)
}

private fun hasTilsynsutgifterFamilie(tilleggstonadJsonObj: JsonApplication): Boolean {
	return false
}

fun convertReiseUtgifter(tilleggstonadJsonObj: JsonApplication): Reiseutgifter? {
	//if (!hasReiseutgifter(tilleggstonadJsonObj.data.data.hvorforReiserDu)) return null

	val utgiftDagligReise = convertDagligReise(tilleggstonadJsonObj)
	val reisestoenadForArbeidssoeker = convertReisestoenadForArbeidssoeker(tilleggstonadJsonObj)
	val reiseVedOppstartOgAvsluttetAktivitet = convertReiseVedOppstartOgAvsluttetAktivitet(tilleggstonadJsonObj)
	val reiseObligatoriskSamling = convertReiseObligatoriskSamling(tilleggstonadJsonObj)

	if (utgiftDagligReise == null && reisestoenadForArbeidssoeker == null
		&& reiseVedOppstartOgAvsluttetAktivitet == null && reiseObligatoriskSamling == null
	) return null

	return Reiseutgifter(
		dagligReise = utgiftDagligReise,
		reiseObligatoriskSamling = reiseObligatoriskSamling,
		reiseVedOppstartOgAvsluttetAktivitet = reiseVedOppstartOgAvsluttetAktivitet,
		reisestoenadForArbeidssoeker = reisestoenadForArbeidssoeker
	)

}

private fun hasReiseutgifter(hasTravelExpenses: HvorforReiserDu?): Boolean {
	if (hasTravelExpenses == null) return false
	return hasTravelExpenses.dagligReise || hasTravelExpenses.reiseNarDuErArbeidssoker
		|| hasTravelExpenses.reiseTilSamling || hasTravelExpenses.reisePaGrunnAvOppstartAvslutningEllerHjemreise
}

private fun convertDagligReise(tilleggstonadJsonObj: JsonApplication): DagligReise? {
	val details = tilleggstonadJsonObj.tilleggsstonad.rettighetstype?.reise
	if (details == null || details.dagligReise == null || details.hvorforReiserDu == null || details.hvorforReiserDu.dagligReise != true) return null

	val jsonDagligReise = details.dagligReise
	return DagligReise(
		periode = convertPeriode("2023-10-01+02:00", "2024-01-31+01:00"), // TODO erstatt med hvilkenPeriodeVilDuSokeFor
		aktivitetsadresse = SammensattAdresse(
			land = jsonDagligReise.velgLand1.label,
			adresse = jsonDagligReise.adresse1,
			postnr = jsonDagligReise.postnr1
		).sammensattAdresse,
		avstand = jsonDagligReise.hvorLangReiseveiHarDu.toDouble(),
		harMedisinskeAarsakerTilTransport = convertToBoolean(jsonDagligReise.kanDuReiseKollektivtDagligReise) ?: false,
		alternativeTransportutgifter = convertAlternativeTransportutgifter_DagligReise(jsonDagligReise),
		innsendingsintervall = convertInnsendingsintervaller(jsonDagligReise.kanIkkeReiseKollektivtDagligReise?.kanBenytteEgenBil?.hvorOfteOnskerDuASendeInnKjoreliste),
		harParkeringsutgift = convertToBoolean(jsonDagligReise.kanIkkeReiseKollektivtDagligReise?.kanDuBenytteEgenBil) ?: false && jsonDagligReise.kanIkkeReiseKollektivtDagligReise?.kanBenytteEgenBil?.oppgiForventetBelopTilParkeringPaAktivitetsstedet ?: 0 > 0,
		parkeringsutgiftBeloep = jsonDagligReise.kanIkkeReiseKollektivtDagligReise?.kanBenytteEgenBil?.oppgiForventetBelopTilParkeringPaAktivitetsstedet
	)
}

private fun convertReisestoenadForArbeidssoeker(tilleggstonadJsonObj: JsonApplication): ReisestoenadForArbeidssoeker? {
	val details = tilleggstonadJsonObj.tilleggsstonad.rettighetstype?.reise
	if (details == null || details.dagligReiseArbeidssoker == null || details.hvorforReiserDu == null || details.hvorforReiserDu.reiseNarDuErArbeidssoker != true) return null

	val dagligReise = details.dagligReiseArbeidssoker
	return ReisestoenadForArbeidssoeker(
		reisedato = convertToDateStringWithTimeZone(date = dagligReise.reisedatoDdMmAaaa),
		harMottattDagpengerSisteSeksMaaneder = convertToBoolean(dagligReise.mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene)
			?: false,
		formaal = Formaal(value = dagligReise.hvorforReiserDuArbeidssoker),
		adresse = SammensattAdresse(
			land = dagligReise.velgLandArbeidssoker.label,
			adresse = dagligReise.adresse,
			postnr = dagligReise.postnr
		).sammensattAdresse,
		avstand = dagligReise.hvorLangReiseveiHarDu3,
		erUtgifterDekketAvAndre = convertToBoolean(dagligReise.dekkerAndreEnnNavEllerDegSelvReisenHeltEllerDelvis) ?: false,
		erVentetidForlenget = convertToBoolean(dagligReise.harMottattDagpengerSiste6Maneder?.harDuHattForlengetVentetidDeSisteAtteUkene)
			?: false,
		finnesTidsbegrensetbortfall = convertToBoolean(dagligReise.harMottattDagpengerSiste6Maneder?.harDuHattTidsbegrensetBortfallDeSisteAtteUkene)
			?: false,
		alternativeTransportutgifter = AlternativeTransportutgifter(
			kanOffentligTransportBrukes = convertToBoolean(dagligReise.kanDuReiseKollektivtArbeidssoker),
			kollektivTransportutgifter = convertKollektivTransportutgifter(dagligReise.hvilkeUtgifterHarDuIForbindelseMedReisen3),
			aarsakTilIkkeOffentligTransport = convertAarsakTilIkkeOffentligTransport(dagligReise.kanIkkeReiseKollektivtArbeidssoker?.hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt),
			kanEgenBilBrukes = convertToBoolean(dagligReise.kanIkkeReiseKollektivtArbeidssoker?.kanDuBenytteEgenBil),
			egenBilTransportutgifter = convertEgenBilTransportutgifter(dagligReise.kanIkkeReiseKollektivtArbeidssoker?.kanBenytteEgenBil),
			drosjeTransportutgifter = convertDrosjeTransportutgifter(dagligReise.kanIkkeReiseKollektivtArbeidssoker?.kanIkkeBenytteEgenBil?.oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIperiodenDuSokerOmStonadFor),
			aarsakTilIkkeDrosje = convertAarsakTilIkkeDrosje(dagligReise.kanIkkeReiseKollektivtArbeidssoker?.kanIkkeBenytteEgenBil?.hvorforKanDuIkkeBenytteDrosje)
		)

	)
}


private fun convertReiseVedOppstartOgAvsluttetAktivitet(tilleggstonadJsonObj: JsonApplication): ReiseVedOppstartOgAvsluttetAktivitet? {
	val details = tilleggstonadJsonObj.tilleggsstonad.rettighetstype?.reise
	if (details == null || details.oppstartOgAvsluttetAktivitet == null || details.hvorforReiserDu == null || details.hvorforReiserDu.reisePaGrunnAvOppstartAvslutningEllerHjemreise != true) return null

	val reiseStartSlutt = details.oppstartOgAvsluttetAktivitet
	return ReiseVedOppstartOgAvsluttetAktivitet(
		periode = convertPeriode("2022-07-11", "2023-08-13") // TODO Bruke HvilkenPeriodeVilDuSokeFor1 når den er ok
		,
		aktivitetsstedAdresse = SammensattAdresse(
			land = reiseStartSlutt.velgLand3.label,
			adresse = reiseStartSlutt.adresse3,
			postnr = reiseStartSlutt.postnr3
		).sammensattAdresse,
		avstand = reiseStartSlutt.hvorLangReiseveiHarDu2 ?: 0,
		antallReiser = reiseStartSlutt.hvorMangeGangerSkalDuReiseEnVei,
		harBarnUnderFemteklasse = convertToBoolean(reiseStartSlutt.harDuBarnSomBorHjemmeOgSomIkkeErFerdigMedFjerdeSkolear)
			?: false,
		harBarnUnderAtten = null // TODO mangler i eksempelet
		,
		alternativeTransportutgifter = AlternativeTransportutgifter(
			kanOffentligTransportBrukes = convertToBoolean(reiseStartSlutt.kanDuReiseKollektivtOppstartAvslutningHjemreise),
			kanEgenBilBrukes = convertToBoolean(reiseStartSlutt.kanIkkeReiseKollektivtOppstartAvslutningHjemreise?.kanDuBenytteEgenBil),
			kollektivTransportutgifter = convertKollektivTransportutgifter(reiseStartSlutt.hvilkeUtgifterHarDuIForbindelseMedReisen4),
			drosjeTransportutgifter = convertDrosjeTransportutgifter(reiseStartSlutt.kanIkkeReiseKollektivtOppstartAvslutningHjemreise?.kanIkkeBenytteEgenBil?.oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIperiodenDuSokerOmStonadFor),
			egenBilTransportutgifter = convertEgenBilTransportutgifter(reiseStartSlutt.kanIkkeReiseKollektivtOppstartAvslutningHjemreise?.kanBenytteEgenBil),
			aarsakTilIkkeOffentligTransport = convertAarsakTilIkkeOffentligTransport(reiseStartSlutt.kanIkkeReiseKollektivtOppstartAvslutningHjemreise?.beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt),
			aarsakTilIkkeEgenBil = convertAarsakTilIkkeEgenBil(reiseStartSlutt.kanIkkeReiseKollektivtOppstartAvslutningHjemreise?.kanIkkeBenytteEgenBil?.hvaErArsakenTilAtDuIkkeKanBenytteEgenBil),
			aarsakTilIkkeDrosje = reiseStartSlutt.kanIkkeReiseKollektivtOppstartAvslutningHjemreise?.kanIkkeBenytteEgenBil?.hvorforKanDuIkkeBenytteDrosje
		)
	)
}


private fun convertReiseObligatoriskSamling(tilleggstonadJsonObj: JsonApplication): ReiseObligatoriskSamling? {
	val details = tilleggstonadJsonObj.tilleggsstonad.rettighetstype?.reise
	if (details == null || details.reiseSamling == null || details.hvorforReiserDu == null || details.hvorforReiserDu.reiseTilSamling != true) return null

	val reiseTilSamling = details.reiseSamling

	if (reiseTilSamling.startOgSluttdatoForSamlingene.isEmpty()) return null // TODO kaste feil?

	val periodeList = createPeriodeList(reiseTilSamling.startOgSluttdatoForSamlingene)

	val startOfPeriods = periodeList.map { convertDateToTimeInMillis(it.fom) }.min()
	val endOfPeriods = periodeList.map { convertDateToTimeInMillis(it.tom) }.max()
	val startDate = convertMillisToDateString(startOfPeriods)
	val endDate = convertMillisToDateString(endOfPeriods)

	return ReiseObligatoriskSamling(
		periode = Periode(
			fom = startDate,
			tom = endDate
		),
		reiseadresser = SammensattAdresse(
			land = reiseTilSamling.velgLandReiseTilSamling.label,
			adresse = reiseTilSamling.adresse2,
			postnr = reiseTilSamling.postnr2
		).sammensattAdresse,
		avstand = reiseTilSamling.hvorLangReiseveiHarDu1 ?: 0,
		samlingsperiode = periodeList,
		alternativeTransportutgifter = AlternativeTransportutgifter(
			kanOffentligTransportBrukes = convertToBoolean(reiseTilSamling.kanDuReiseKollektivtReiseTilSamling),
			// TODO Trenger ikke spesifisering av utgift pr reise
			// TODO burde vært liste med utgift pr samling?
			kollektivTransportutgifter = convertKollektivTransportutgifter(
				reiseTilSamling.kanReiseKollektivt?.hvilkeUtgifterHarDuIForbindelseMedReisen1
			),
			kanEgenBilBrukes = convertToBoolean(reiseTilSamling.kanIkkeReiseKollektivtReiseTilSamling?.kanDuBenytteEgenBil),
			egenBilTransportutgifter = convertEgenBilTransportutgifter(reiseTilSamling.kanIkkeReiseKollektivtReiseTilSamling?.kanBenytteEgenBil),
			drosjeTransportutgifter = convertDrosjeTransportutgifter(reiseTilSamling.kanIkkeReiseKollektivtReiseTilSamling?.kanIkkeBenytteEgenBil?.oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIperiodenDuSokerOmStonadFor),
			aarsakTilIkkeOffentligTransport = convertAarsakTilIkkeOffentligTransport(reiseTilSamling.kanIkkeReiseKollektivtReiseTilSamling?.hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt),
			aarsakTilIkkeEgenBil = convertAarsakTilIkkeEgenBil(reiseTilSamling.kanIkkeReiseKollektivtReiseTilSamling?.kanIkkeBenytteEgenBil?.hvaErArsakenTilAtDuIkkeKanBenytteEgenBil),
			aarsakTilIkkeDrosje = convertAarsakTilIkkeDrosje(reiseTilSamling.kanIkkeReiseKollektivtReiseTilSamling?.kanIkkeBenytteEgenBil?.hvorforKanDuIkkeBenytteDrosje)
		)

	)
}

fun convertMillisToDateString(millis: Long): String {
	val format = "yyyy-MM-ddXXX"
	val date = Date(millis)
	val sdf = SimpleDateFormat(format)
	return sdf.format(date)
}

private fun convertDateToTimeInMillis(date: String): Long {
	val format = "yyyy-MM-ddXXX"
	val dateFormat = SimpleDateFormat(format)
	return dateFormat.parse(date).time
}

private fun convertToBoolean(string: String?): Boolean? {
	if (string == null) return null
	return "JA".equals(string, true)
}

private fun createPeriodeList(fromJsonPeriodes: List<StartOgSluttdatoForSamlingene>): List<Periode> {
	return fromJsonPeriodes.map { convertPeriode(fom = it.startdatoDdMmAaaa, tom = it.sluttdatoDdMmAaaa) }.toList()
}

private fun convertPeriode(fom: String, tom: String): Periode {
	return Periode(fom = convertToDateStringWithTimeZone(fom), tom = convertToDateStringWithTimeZone(tom))
// TODO bruker startOgSluttdatoForSamlingene da dette er eneste fornuftige i json eksempelet, HvilkenPeriodeVilDuSokeFor kan ikke benyttes.
// TODO vi må bli enig om korrekt mapping av data format. I "fasit skal det være YYYY-MM-DD+HH:MM (kooreksjon i forhold til GMT)
}

private fun convertAdresse(adresse: String, postnummer: String, landKode: String): String {
	return adresse + ", " + postnummer + ", " + landKode // TODO uklar mapping av felter.
}

private fun convertAlternativeTransportutgifter_DagligReise(details: JsonDagligReise): AlternativeTransportutgifter {
	val kanBenytteEgenBil = convertToBoolean(details.kanIkkeReiseKollektivtDagligReise?.kanDuBenytteEgenBil) ?: false
	return AlternativeTransportutgifter(
		kanOffentligTransportBrukes = convertToBoolean(details.kanDuReiseKollektivtDagligReise),
		kanEgenBilBrukes = kanBenytteEgenBil,
		kollektivTransportutgifter = convertKollektivTransportutgifter(details.hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise),
		drosjeTransportutgifter = convertDrosjeTransportutgifter(details.kanIkkeReiseKollektivtDagligReise?.kanIkkeBenytteEgenBilDagligReise?.oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIperiodenDuSokerOmStonadFor),
		egenBilTransportutgifter = convertEgenBilTransportutgifter(details.kanIkkeReiseKollektivtDagligReise?.kanBenytteEgenBil),
		aarsakTilIkkeOffentligTransport = convertAarsakTilIkkeOffentligTransport(details.kanIkkeReiseKollektivtDagligReise?.beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt),
		aarsakTilIkkeEgenBil = convertAarsakTilIkkeEgenBil(details.kanIkkeReiseKollektivtDagligReise?.kanIkkeBenytteEgenBilDagligReise?.hvaErArsakenTilAtDuIkkeKanBenytteEgenBil),
		aarsakTilIkkeDrosje = convertAarsakTilIkkeDrosje(details.kanIkkeReiseKollektivtDagligReise?.kanIkkeBenytteEgenBilDagligReise?.hvorforKanDuIkkeBenytteDrosje)
	)
}

private fun convertKollektivTransportutgifter(utgifter: Int?): KollektivTransportutgifter? {
	if (utgifter == null) return null
	return KollektivTransportutgifter(beloepPerMaaned = utgifter)
}

private fun convertDrosjeTransportutgifter(utgifter: Int?): DrosjeTransportutgifter? {
	if (utgifter == null) return null
	return DrosjeTransportutgifter(beloep = utgifter)
}

private fun convertInnsendingsintervaller(details: String?): Innsendingsintervaller? {
	if (details == null) return null
	return when (details) {
		"UKE", "MND" -> Innsendingsintervaller(details)
		else -> Innsendingsintervaller("UKE")
	}
}

private fun convertEgenBilTransportutgifter(utgifter: KanBenytteEgenBil?): EgenBilTransportutgifter? {
	if (utgifter == null) return null
	return EgenBilTransportutgifter(
		sumAndreUtgifter = ((utgifter.annet ?: 0) + (utgifter.bompenger ?: 0)
			+ (utgifter.ferje ?: 0) + (utgifter.piggdekkavgift ?: 0)).toDouble()
	)
}

private fun convertAarsakTilIkkeOffentligTransport(aarsakTilIkkeOffentligTransport: String?): List<String>? {
	if (aarsakTilIkkeOffentligTransport == null) return null
	return listOf(aarsakTilIkkeOffentligTransport)
}

private fun convertAarsakTilIkkeEgenBil(ikkeEgenBil: String?): List<String>? {
	if (ikkeEgenBil == null) return null
	return listOf(ikkeEgenBil)
}

private fun convertAarsakTilIkkeDrosje(aarsak: String?): String? {
	if (aarsak.isNullOrBlank()) return null
	return aarsak
}



