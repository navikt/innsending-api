package no.nav.soknad.innsending.util.mapping.tilleggsstonad

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.model.DokumentSoknadDto
import java.text.SimpleDateFormat
import java.util.*


fun json2Xml(soknadDto: DokumentSoknadDto, jsonFil: ByteArray?): ByteArray {
	// Konverter json-string til tilleggsstonadJson
	val tilleggstonadJsonObj =
		convertToJsonTilleggsstonad(soknadDto, jsonFil)

	// Map tilleggsstonadFraJson til tilleggsstonadXML
	val tilleggsstonadXmlObj = convertToTilleggsstonadsskjema(soknadDto, tilleggstonadJsonObj)

	// Konverter tilleggsstonadXML til xml-string
	return convertToXml(tilleggsstonadXmlObj)
}

fun json2Xml(soknadDto: DokumentSoknadDto, tilleggstonadJsonObj: JsonApplication<JsonTilleggsstonad>): ByteArray {

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
	xmlMapper.setDateFormat(SimpleDateFormat("yyyy-MM-ddXXX", Locale.of("nb", "NO")))
	xmlMapper.registerModule(JaxbAnnotationModule())
	val xml =
		"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" + xmlMapper.writeValueAsString(tilleggsstonad)
	return xml.toByteArray()
}

fun convertToTilleggsstonadsskjema(
	soknadDto: DokumentSoknadDto,
	tilleggstonadJsonObj: JsonApplication<JsonTilleggsstonad>
): Tilleggsstoenadsskjema {
	return Tilleggsstoenadsskjema(
		aktivitetsinformasjon = convertToAktivitetsinformasjon(tilleggstonadJsonObj.applicationDetails.aktivitetsinformasjon),
		personidentifikator = soknadDto.brukerId,
		maalgruppeinformasjon = convertToMaalgruppeinformasjon(tilleggstonadJsonObj.applicationDetails.maalgruppeinformasjon),
		rettighetstype = convertToRettighetstype(soknadDto, tilleggstonadJsonObj.applicationDetails.rettighetstype)
	)

}

fun convertToAktivitetsinformasjon(jsonAktivitetsInformasjon: JsonAktivitetsInformasjon?): Aktivitetsinformasjon? {
	if (jsonAktivitetsInformasjon?.aktivitet == null) return null
	return Aktivitetsinformasjon(jsonAktivitetsInformasjon.aktivitet)
}

fun convertToMaalgruppeinformasjon(jsonMaalgruppeinformasjon: JsonMaalgruppeinformasjon?): Maalgruppeinformasjon? {
	return if (jsonMaalgruppeinformasjon != null) {
		Maalgruppeinformasjon(
			periode = if (jsonMaalgruppeinformasjon.periode != null
				&& jsonMaalgruppeinformasjon.periode.startdatoDdMmAaaa.isNotBlank() && jsonMaalgruppeinformasjon.periode.sluttdatoDdMmAaaa.isNotBlank()
			)
				Periode(
					fom = convertToDateStringWithTimeZone(jsonMaalgruppeinformasjon.periode.startdatoDdMmAaaa),
					tom = convertToDateStringWithTimeZone(jsonMaalgruppeinformasjon.periode.sluttdatoDdMmAaaa)
				) else null,
			kilde = jsonMaalgruppeinformasjon.kilde,
			maalgruppetype = Maalgruppetyper(
				value = jsonMaalgruppeinformasjon.maalgruppetype,
				kodeverksRef = jsonMaalgruppeinformasjon.maalgruppetype
			)
		)
	} else {
		null
	}
}

fun convertToDateStringWithTimeZone(date: String): String {
	val inputFormat = SimpleDateFormat("yyyy-MM-dd")
	val inputDate = inputFormat.parse(date.substring(0, 10))
	val outputFormat = SimpleDateFormat("yyyy-MM-ddXXX", Locale.of("no", "NO"))
	return outputFormat.format(inputDate)
}

fun convertToRettighetstype(soknadDto: DokumentSoknadDto, jsonRettighetstyper: JsonRettighetstyper?): Rettighetstype {
	if (jsonRettighetstyper == null) throw BackendErrorException("${soknadDto.innsendingsId}: Rettighettype data mangler i søknaden")
	return Rettighetstype(
		boutgifter = convertBostotte(jsonRettighetstyper),
		laeremiddelutgifter = convertLaremiddler(jsonRettighetstyper),
		flytteutgifter = convertFlytteutgifter(jsonRettighetstyper),
		tilsynsutgifter = convertTilsynsutgifter(jsonRettighetstyper),
		reiseutgifter = convertReiseUtgifter(jsonRettighetstyper, soknadDto)
	)
}

fun convertBostotte(jsonRettighetstyper: JsonRettighetstyper): Boutgifter? {
	if (jsonRettighetstyper.bostotte?.aktivitetsperiode == null
	) return null

	val bostottesoknad = jsonRettighetstyper.bostotte
	return Boutgifter(
		periode = Periode(
			fom = convertToDateStringWithTimeZone(bostottesoknad.aktivitetsperiode.startdatoDdMmAaaa),
			tom = convertToDateStringWithTimeZone(bostottesoknad.aktivitetsperiode.sluttdatoDdMmAaaa)
		),
		mottarBostoette = convertToBoolean(bostottesoknad.mottarDuBostotteFraKommunen) ?: false,
		bostoetteBeloep = bostottesoknad.bostottebelop,

		boutgifterPgaFunksjonshemminger = convertToBoolean(bostottesoknad.erDetMedisinskeForholdSomPavirkerUtgifteneDinePaAktivitetsstedet)
			?: false,
		harFasteBoutgifter = bostottesoknad.hvilkeAdresserHarDuBoutgifterPa.boutgifterPaHjemstedet,
		boutgifterHjemstedAktuell = bostottesoknad.boutgifterPaHjemstedetMitt,
		boutgifterHjemstedOpphoert = bostottesoknad.boutgifterJegHarHattPaHjemstedetMittMenSomHarOpphortIForbindelseMedAktiviteten,

		boutgifterAktivitetsted = bostottesoknad.boutgifterPaAktivitetsadressen,

		harBoutgifterVedSamling = bostottesoknad.hvilkeBoutgifterSokerDuOmAFaDekket.contains("boutgifterIForbindelseMedSamling"),
		samlingsperiode = if (bostottesoknad.bostotteIForbindelseMedSamling?.periodeForSamling == null) null else {
			bostottesoknad.bostotteIForbindelseMedSamling.periodeForSamling
				.map {
					Periode(
						fom = convertToDateStringWithTimeZone(it.startdatoDdMmAaaa),
						tom = convertToDateStringWithTimeZone(it.sluttdatoDdMmAaaa)
					)
				}
		},
	)

}

fun convertLaremiddler(jsonRettighetstyper: JsonRettighetstyper): Laeremiddelutgifter? {
	if (jsonRettighetstyper.laeremiddelutgifter == null) return null

	val laeremiddelutgifter = jsonRettighetstyper.laeremiddelutgifter

	return Laeremiddelutgifter(
		periode = convertPeriode(
			fom = laeremiddelutgifter.aktivitetsperiode.startdatoDdMmAaaa,
			tom = laeremiddelutgifter.aktivitetsperiode.sluttdatoDdMmAaaa
		),
		hvorMyeDekkesAvAnnenAktoer = laeremiddelutgifter.hvorMyeFarDuDekketAvEnAnnenAktor?.toDouble(), //  Blir aldri satt i gammel løsning
		hvorMyeDekkesAvNAV = laeremiddelutgifter.hvorStortBelopSokerDuOmAFaDekketAvNav?.toDouble(), // Blir aldri satt i gammel løsning
		skolenivaa = convertToSkolenvaaer(laeremiddelutgifter.hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore),
		prosentandelForUtdanning = laeremiddelutgifter.oppgiHvorMangeProsentDuStudererEllerGarPaKurs,
		beloep = laeremiddelutgifter.utgifterTilLaeremidler, // Kun satt dersom funksjonshemning
		erUtgifterDekket = convertToErUtgifterDekket(laeremiddelutgifter.farDuDekketLaeremidlerEtterAndreOrdninger)
	)
}

fun convertToSkolenvaaer(nivaString: String): Skolenivaaer =
	when (nivaString) {
		"videregaendeUtdanning" -> Skolenivaaer(SkolenivaaerKodeverk.videregaende.kodeverk)
		"hoyereUtdanning" -> Skolenivaaer(SkolenivaaerKodeverk.hoyereutdanning.kodeverk)
		"kursEllerAnnenUtdanning" -> Skolenivaaer(SkolenivaaerKodeverk.annet.kodeverk)
		else -> Skolenivaaer(SkolenivaaerKodeverk.annet.kodeverk)
	}

fun convertToErUtgifterDekket(svar: String): ErUtgifterDekket =
	when (svar.uppercase(Locale.getDefault())) {
		"JA" -> ErUtgifterDekket(value = ErUtgifterDekketKodeverk.ja.kodeverk)
		"NEI" -> ErUtgifterDekket(value = ErUtgifterDekketKodeverk.nei.kodeverk)
		"DELVIS" -> ErUtgifterDekket(value = ErUtgifterDekketKodeverk.delvis.kodeverk)
		else -> ErUtgifterDekket(value = ErUtgifterDekketKodeverk.nei.kodeverk)
	}


fun convertFlytteutgifter(jsonRettighetstyper: JsonRettighetstyper): Flytteutgifter? {
	if (jsonRettighetstyper.flytteutgifter == null) return null

	val flytteutgifter = jsonRettighetstyper.flytteutgifter

	return Flytteutgifter(
		flyttingPgaAktivitet =
			flytteutgifter.hvorforFlytterDu.equals("aktivitet", true),
		erUtgifterTilFlyttingDekketAvAndreEnnNAV = convertToBoolean(flytteutgifter.farDuDekketUtgifteneDineTilFlyttingPaAnnenMateEnnMedStonadFraNav)
			?: false,
		flytterSelv = (flytteutgifter.jegFlytterSelv != null || flytteutgifter.jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv != null).toString(),
		flyttingPgaNyStilling = flytteutgifter.hvorforFlytterDu.equals("nyJobb", true),
		flyttedato = convertToDateStringWithTimeZone(flytteutgifter.narFlytterDuDdMmAaaa),
		tilflyttingsadresse = SammensattAdresse(
			land = flytteutgifter.velgLand1.label,
			adresse = flytteutgifter.adresse1,
			postnr = flytteutgifter.postnr1
		).sammensattAdresse,
		avstand = convertFlytteAvstand(flytteutgifter),
		sumTilleggsutgifter = convertFlytteutgifter(flytteutgifter)?.toDouble(),
		anbud = comvertAnbud(flytteutgifter),
		valgtFlyttebyraa = flytteutgifter.jegVilBrukeFlyttebyra?.jegVelgerABruke
	)
}

fun comvertAnbud(flytteutgifter: JsonFlytteutgifter): List<Anbud>? {
	if (flytteutgifter.jegVilBrukeFlyttebyra == null) return null

	return listOf(
		Anbud(firmanavn = flytteutgifter.jegVilBrukeFlyttebyra.navnPaFlyttebyra1, tilbudsbeloep = flytteutgifter.jegVilBrukeFlyttebyra.belop),
		Anbud(firmanavn = flytteutgifter.jegVilBrukeFlyttebyra.navnPaFlyttebyra2, tilbudsbeloep = flytteutgifter.jegVilBrukeFlyttebyra.belop1)
	)

}

fun convertFlytteAvstand(flytteutgifter: JsonFlytteutgifter): Int {
	return if (flytteutgifter.jegFlytterSelv != null) {
		flytteutgifter.jegFlytterSelv.hvorLangtSkalDuFlytte
	} else if (flytteutgifter.jegVilBrukeFlyttebyra != null) {
		flytteutgifter.jegVilBrukeFlyttebyra.hvorLangtSkalDuFlytte1
	} else if (flytteutgifter.jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv != null) {
		flytteutgifter.jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv.hvorLangtSkalDuFlytte1
	} else {
		return 0
	}

}

fun convertFlytteutgifter(flytteutgifter: JsonFlytteutgifter): Int? {
	if (flytteutgifter.jegFlytterSelv != null) {
		val flytterSelv = flytteutgifter.jegFlytterSelv
		return (flytterSelv.bom ?: 0) + (flytterSelv.annet ?: 0) + (flytterSelv.hengerleie ?: 0) + (flytterSelv.parkering
			?: 0) + (flytterSelv.ferje ?: 0)
	} else if (flytteutgifter.jegVilBrukeFlyttebyra != null) {
		return null
	} else if (flytteutgifter.jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv != null) {
		val flytterSelv = flytteutgifter.jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv
		return (flytterSelv.bom ?: 0) + (flytterSelv.annet ?: 0) + (flytterSelv.hengerleie ?: 0) + (flytterSelv.parkering
			?: 0) + (flytterSelv.ferje ?: 0)
	} else {
		return null
	}
}

fun convertTilsynsutgifter(jsonRettighetstyper: JsonRettighetstyper): Tilsynsutgifter? {

	val utgifterBarn = convertTilsynsutgifterBarn(jsonRettighetstyper)
	val utgifterFamilie = convertFamilieutgifter(jsonRettighetstyper) // TODO er denne relevant?

	if (utgifterBarn == null && utgifterFamilie == null) return null

	return Tilsynsutgifter(tilsynsutgifterBarn = utgifterBarn, tilynsutgifterFamilie = utgifterFamilie)
}

private fun convertTilsynsutgifterBarn(jsonRettighetstyper: JsonRettighetstyper): TilsynsutgifterBarn? {
	if (jsonRettighetstyper.tilsynsutgifter == null) return null

	val tilsynsutgifter = jsonRettighetstyper.tilsynsutgifter
	return TilsynsutgifterBarn(
		Periode(
			fom = convertToDateStringWithTimeZone(tilsynsutgifter.aktivitetsPeriode.startdatoDdMmAaaa),
			tom = convertToDateStringWithTimeZone(tilsynsutgifter.aktivitetsPeriode.sluttdatoDdMmAaaa)
		),
		barn = tilsynsutgifter.barnePass.filter { it.jegSokerOmStonadTilPassAvDetteBarnet ?: false }.map {
			Barn(
				personidentifikator = it.fodselsdatoDdMmAaaa,
				tilsynskategori = Tilsynskategorier(convertTilsynskategori(it.sokerStonadForDetteBarnet?.hvemPasserBarnet)),
				navn = it.fornavn + " " + it.etternavn,
				harFullfoertFjerdeSkoleaar = convertToBoolean(it.sokerStonadForDetteBarnet?.harBarnetFullfortFjerdeSkolear)
					?: false,
				aarsakTilBarnepass = convertAarsakTilBarnepass(it.sokerStonadForDetteBarnet?.hvaErArsakenTilAtBarnetDittTrengerPass),
				maanedligUtgiftTilsynBarn = it.sokerStonadForDetteBarnet?.oppgiManedligUtgiftTilBarnepass ?: 0
			)
		},
		annenForsoergerperson = tilsynsutgifter.fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa
	)
}

private fun convertAarsakTilBarnepass(aarsak: String?): List<String>? {
	if (aarsak == null) return null
	when (aarsak) {
		"langvarigUregelmessigFravaer" -> return listOf(BarnepassAarsak.langvarig.cmsKey)
		"saerligBehovForPass" -> return listOf(BarnepassAarsak.trengertilsyn.cmsKey)
		else -> return listOf(BarnepassAarsak.ingen.cmsKey)
	}

}

private fun convertTilsynskategori(kategori: String?): String {
	when (kategori) {
		"dagmammaEllerDagpappa" -> return TilsynForetasAvKodeverk.dagmamma.kodeverksverdi // KOM
		"barnehageEllerSfo" -> return TilsynForetasAvKodeverk.barnehage.kodeverksverdi // OFF
		"privatOrdning" -> return TilsynForetasAvKodeverk.privat.kodeverksverdi // PRI
		else -> return "KOM"
	}
}

// IKKE I BRUK I GAMMEL LØSNING
private fun convertFamilieutgifter(jsonRettighetstyper: JsonRettighetstyper): TilsynsutgifterFamilie? {
	if (!hasTilsynsutgifterFamilie(jsonRettighetstyper)) return null

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

private fun hasTilsynsutgifterFamilie(jsonRettighetstyper: JsonRettighetstyper): Boolean {
	return false
}

fun convertReiseUtgifter(jsonRettighetstyper: JsonRettighetstyper, soknadDto: DokumentSoknadDto): Reiseutgifter? {

	val utgiftDagligReise = convertDagligReise(jsonRettighetstyper, soknadDto)
	val reisestoenadForArbeidssoeker = convertReisestoenadForArbeidssoeker(jsonRettighetstyper)
	val reiseVedOppstartOgAvsluttetAktivitet = convertReiseVedOppstartOgAvsluttetAktivitet(jsonRettighetstyper)
	val reiseObligatoriskSamling = convertReiseObligatoriskSamling(jsonRettighetstyper)

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

private fun convertDagligReise(jsonRettighetstyper: JsonRettighetstyper, soknadDto: DokumentSoknadDto): DagligReise? {
	val details = jsonRettighetstyper.reise
	if (details == null || (details.dagligReise == null)) return null

	val jsonDagligReise = details.dagligReise
	return DagligReise(
		periode = convertPeriode(jsonDagligReise.startdatoDdMmAaaa, jsonDagligReise.sluttdatoDdMmAaaa),
		aktivitetsadresse = SammensattAdresse(
			land = jsonDagligReise.velgLand1.label,
			adresse = jsonDagligReise.adresse1,
			postnr = jsonDagligReise.postnr1
		).sammensattAdresse,
		avstand = jsonDagligReise.hvorLangReiseveiHarDu.toDouble(),
		harMedisinskeAarsakerTilTransport = convertToBoolean(jsonDagligReise.kanDuReiseKollektivtDagligReise) ?: false,
		alternativeTransportutgifter = convertAlternativeTransportutgifter_DagligReise(jsonDagligReise),
		innsendingsintervall = convertInnsendingsintervaller(jsonDagligReise.kanIkkeReiseKollektivtDagligReise?.kanBenytteEgenBil?.hvorOfteOnskerDuASendeInnKjoreliste),
		harParkeringsutgift = (convertToBoolean(jsonDagligReise.kanIkkeReiseKollektivtDagligReise?.kanDuBenytteEgenBil)
			?: false) && ((jsonDagligReise.kanIkkeReiseKollektivtDagligReise?.kanBenytteEgenBil?.parkering ?: 0) > 0),
		parkeringsutgiftBeloep = jsonDagligReise.kanIkkeReiseKollektivtDagligReise?.kanBenytteEgenBil?.parkering
	)
}

private fun convertReisestoenadForArbeidssoeker(jsonRettighetstyper: JsonRettighetstyper): ReisestoenadForArbeidssoeker? {
	val details = jsonRettighetstyper.reise
	if (details == null || details.dagligReiseArbeidssoker == null) return null

	val dagligReise = details.dagligReiseArbeidssoker
	return ReisestoenadForArbeidssoeker(
		reisedato = convertToDateStringWithTimeZone(date = dagligReise.reisedatoDdMmAaaa),
		harMottattDagpengerSisteSeksMaaneder = convertToBoolean(dagligReise.mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene)
			?: false,
		formaal = Formaal(value = convertToFormaal(dagligReise.hvorforReiserDuArbeidssoker)),
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

private fun convertToFormaal(formaalEnumValue: String): String {
	// FormaalKodeverk
	when (formaalEnumValue) {
		"oppfolgingFraNav" -> return FormaalKodeverk.oppfolging.kodeverksverdi
		"jobbintervju" -> return  FormaalKodeverk.jobbintervju.kodeverksverdi
		"arbeidPaNyttSted" -> return FormaalKodeverk.tiltraa.kodeverksverdi
		else -> return FormaalKodeverk.oppfolging.kodeverksverdi
	}
}


private fun convertReiseVedOppstartOgAvsluttetAktivitet(jsonRettighetstyper: JsonRettighetstyper): ReiseVedOppstartOgAvsluttetAktivitet? {
	val details = jsonRettighetstyper.reise
	if (details == null || details.oppstartOgAvsluttetAktivitet == null) return null

	val reiseStartSlutt = details.oppstartOgAvsluttetAktivitet
	return ReiseVedOppstartOgAvsluttetAktivitet(
		periode = convertPeriode(reiseStartSlutt.startdatoDdMmAaaa1, reiseStartSlutt.sluttdatoDdMmAaaa1),
		aktivitetsstedAdresse = SammensattAdresse(
			land = reiseStartSlutt.velgLand3.label,
			adresse = reiseStartSlutt.adresse3,
			postnr = reiseStartSlutt.postnr3
		).sammensattAdresse,
		avstand = reiseStartSlutt.hvorLangReiseveiHarDu2 ?: 0,
		antallReiser = reiseStartSlutt.hvorMangeGangerSkalDuReiseEnVei,
		harBarnUnderFemteklasse = convertToBoolean(reiseStartSlutt.harDuBarnSomBorHjemmeOgSomIkkeErFerdigMedFjerdeSkolear)
			?: false,
		harBarnUnderAtten = convertToBoolean(reiseStartSlutt.harDuBarnSomSkalFlytteMedDeg) ?: false,
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


private fun convertReiseObligatoriskSamling(jsonRettighetstyper: JsonRettighetstyper): ReiseObligatoriskSamling? {
	val details = jsonRettighetstyper.reise
	if (details == null || details.reiseSamling == null) return null

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

private fun createPeriodeList(fromJsonPeriodes: List<JsonPeriode>): List<Periode> {
	return fromJsonPeriodes.map { convertPeriode(fom = it.startdatoDdMmAaaa, tom = it.sluttdatoDdMmAaaa) }.toList()
}

private fun convertPeriode(fom: String, tom: String): Periode {
	return Periode(fom = convertToDateStringWithTimeZone(fom), tom = convertToDateStringWithTimeZone(tom))
}


private fun convertAlternativeTransportutgifter_DagligReise(details: JsonDagligReise): AlternativeTransportutgifter {
	val kanBenytteEgenBil = convertToBoolean(details.kanIkkeReiseKollektivtDagligReise?.kanDuBenytteEgenBil)
	return AlternativeTransportutgifter(
		kanOffentligTransportBrukes = convertToBoolean(details.kanDuReiseKollektivtDagligReise),
		kanEgenBilBrukes = kanBenytteEgenBil,
		kollektivTransportutgifter = convertKollektivTransportutgifter(details.hvilkeUtgifterHarDuIForbindelseMedReisenDagligReise),
		drosjeTransportutgifter = convertDrosjeTransportutgifter(details.kanIkkeReiseKollektivtDagligReise?.kanIkkeBenytteEgenBil?.oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIperiodenDuSokerOmStonadFor),
		egenBilTransportutgifter = convertEgenBilTransportutgifter(details.kanIkkeReiseKollektivtDagligReise?.kanBenytteEgenBil, true),
		aarsakTilIkkeOffentligTransport = convertAarsakTilIkkeOffentligTransport(details.kanIkkeReiseKollektivtDagligReise?.hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt),
		aarsakTilIkkeEgenBil = convertAarsakTilIkkeEgenBil(details.kanIkkeReiseKollektivtDagligReise?.kanIkkeBenytteEgenBil?.hvaErArsakenTilAtDuIkkeKanBenytteEgenBil),
		aarsakTilIkkeDrosje = convertAarsakTilIkkeDrosje(details.kanIkkeReiseKollektivtDagligReise?.kanIkkeBenytteEgenBil?.hvorforKanDuIkkeBenytteDrosje)
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

private fun convertEgenBilTransportutgifter(utgifter: KanBenytteEgenBil?, isDagligReise: Boolean = false): EgenBilTransportutgifter? {
	if (utgifter == null) return null
	return EgenBilTransportutgifter(
		sumAndreUtgifter = ((utgifter.annet ?: 0) + (utgifter.bompenger ?: 0) + (if (isDagligReise) 0 else utgifter.parkering ?: 0)
			+ (utgifter.ferje ?: 0) + (utgifter.piggdekkavgift ?: 0)).toDouble()
	)
}

// Årsak til ikke offentlig transport er definert i XML, men aldri inkludert i koden i sendsoknad.
private fun convertAarsakTilIkkeOffentligTransport(aarsakTilIkkeOffentligTransport: String?): List<String>? {
	if (aarsakTilIkkeOffentligTransport == null) return null
	return listOf(aarsakTilIkkeOffentligTransport)
}

// Årsak til ikke egen bil er definert i XML, men aldri inkludert i koden i sendsoknad.
private fun convertAarsakTilIkkeEgenBil(ikkeEgenBil: String?): List<String>? {
	if (ikkeEgenBil == null) return null
	return listOf(ikkeEgenBil)
}

// Årsak til ikke drosje er definert i XML, men aldri inkludert i koden i sendsoknad.
private fun convertAarsakTilIkkeDrosje(aarsak: String?): String? {
	if (aarsak.isNullOrBlank()) return null
	return aarsak
}



