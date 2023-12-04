package no.nav.soknad.innsending.util.mapping

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.DokumentSoknadDto
import java.text.SimpleDateFormat
import java.util.*


fun json2Xml(soknadDto: DokumentSoknadDto, jsonFil: ByteArray?): ByteArray {
	// Konverter json-string til tilleggsstonadJson
	val tilleggstonadJsonObj = convertToJson(soknadDto, jsonFil)

	// Map tilleggsstonadFraJson til tilleggsstonadXML
	val tilleggsstonadXmlObj = convertToTilleggsstonadsskjema(soknadDto, tilleggstonadJsonObj)

	// Konverter tilleggsstonadXML til xml-string
	return convertToXml(tilleggsstonadXmlObj)
}

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

private fun convertToJsonTilleggsstonad(tilleggsstonad: Application): JsonTilleggsstonad {
	return JsonTilleggsstonad(
		aktivitetsinformasjon = JsonAktivitetsInformasjon("12345"),
		maalgruppeinformasjon = JsonMaalgruppeinformasjon("TODO"),
		rettighetstype = convertToJsonRettighetstyper(tilleggsstonad)

	)
}

private fun convertToJsonRettighetstyper(tilleggsstonad: Application): JsonRettighetstyper {
	return JsonRettighetstyper(
		reise = convertToReisestottesoknad(tilleggsstonad)
	)
}

private fun convertToReisestottesoknad(tilleggsstonad: Application): JsonReisestottesoknad {
	return JsonReisestottesoknad(
		hvorforReiserDu = tilleggsstonad.hvorforReiserDu,
		dagligReise = if (tilleggsstonad.hvorforReiserDu?.dagligReise == true) convertToJsonDagligReise(tilleggsstonad) else null,
		reiseSamling = if (tilleggsstonad.hvorforReiserDu?.reiseTilSamling == true) convertToJsonReiseSamling(tilleggsstonad) else null,
		dagligReiseArbeidssoker = if (tilleggsstonad.hvorforReiserDu?.reiseNarDuErArbeidssoker == true) convertToJsonReise_Arbeidssoker(
			tilleggsstonad
		) else null,
		oppstartOgAvsluttetAktivitet = if (tilleggsstonad.hvorforReiserDu?.reisePaGrunnAvOppstartAvslutningEllerHjemreise == true) convertToJsonOppstartOgAvsluttetAktivitet(
			tilleggsstonad
		) else null
	)
}

private fun <T : Any> validateNoneNull(input: T?, field: String): T {
	return input ?: throw IllegalActionException("Mangler input for $field")
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
	if (!hasBoUtgifter(tilleggstonadJsonObj)) return null

	// TODO eksempel op oppretting av Boutgifter
	return Boutgifter(
		periode = Periode(
			fom = convertToDateStringWithTimeZone("2023-12-01+01:00"),
			tom = convertToDateStringWithTimeZone("2024-03-30+01:00")
		),
		harFasteBoutgifter = true,
		harBoutgifterVedSamling = false,
		boutgifterHjemstedAktuell = 8000,
		boutgifterPgaFunksjonshemminger = null,
		mottarBostoette = false,
		samlingsperiode = null
	)

}

private fun hasBoUtgifter(tilleggstonadJsonObj: JsonApplication): Boolean {
	//val details = tilleggstonadJsonObj.tilleggsstonad.rettighetstype?.boutgifter
	return false
}

fun convertLaremiddler(tilleggstonadJsonObj: JsonApplication): Laeremiddelutgifter? {
	if (!hasLaeremiddelutgifter(tilleggstonadJsonObj)) return null

	// TODO eksempel på oppretting av Laeremiddelutgifter
	return Laeremiddelutgifter(
		periode = convertPeriode(fom = "2024-01-01", tom = "2024-12-18"),
		hvorMyeDekkesAvAnnenAktoer = null,
		hvorMyeDekkesAvNAV = "100".toDouble(),
		skolenivaa = Skolenivaaer(value = "HGU"),
		prosentandelForUtdanning = 100,
		beloep = 2000,
		erUtgifterDekket = ErUtgifterDekket("DEL")
	)
}

private fun hasLaeremiddelutgifter(tilleggstonadJsonObj: JsonApplication): Boolean {
	return false
}

fun convertFlytteutgifter(tilleggstonadJsonObj: JsonApplication): Flytteutgifter? {
	if (!hasFlytteutgifter(tilleggstonadJsonObj)) return null

	// TODO, eksempel
	return Flytteutgifter(
		flyttingPgaAktivitet = true,
		erUtgifterTilFlyttingDekketAvAndreEnnNAV = false,
		flytterSelv = "true",
		flyttingPgaNyStilling = false,
		flyttedato = convertToDateStringWithTimeZone("2023-12-24"),
		tilflyttingsadresse = SammensattAdresse(
			land = null,
			adresse = "Kongens Gate 1",
			postnr = "3701"
		).sammensattAdresse,
		avstand = 130,
		sumTilleggsutgifter = 10000.00
	)

}

private fun hasFlytteutgifter(tilleggstonadJsonObj: JsonApplication): Boolean {
	return false
}

fun convertTilsynsutgifter(tilleggstonadJsonObj: JsonApplication): Tilsynsutgifter? {
	if (!hasTilsynsutgifter(tilleggstonadJsonObj)) return null

	val utgifterBarn = convertTilsynsutgifterBarn(tilleggstonadJsonObj)
	val utgifterFamilie = convertFamilieutgifter(tilleggstonadJsonObj)

	if (utgifterBarn == null && utgifterFamilie == null) return null

	return Tilsynsutgifter(tilsynsutgifterBarn = utgifterBarn, tilynsutgifterFamilie = utgifterFamilie)
}

private fun hasTilsynsutgifter(tilleggstonadJsonObj: JsonApplication): Boolean {
	return false
}

private fun convertTilsynsutgifterBarn(tilleggstonadJsonObj: JsonApplication): TilsynsutgifterBarn? {
	if (!hasTilsynsutgifterBarn(tilleggstonadJsonObj)) return null

	// TODO erstatt eksempel nedenfor
	return TilsynsutgifterBarn(
		Periode(
			fom = convertToDateStringWithTimeZone("2023-12-01+01:00"),
			tom = convertToDateStringWithTimeZone("2024-03-30+01:00")
		), barn = listOf(
			Barn(
				personidentifikator = "12345678901", tilsynskategori = Tilsynskategorier("KOM", ""),
				"Morsomt Navn", harFullfoertFjerdeSkoleaar = false, maanedligUtgiftTilsynBarn = 4500
			),
			Barn(
				personidentifikator = "12345678902", tilsynskategori = Tilsynskategorier("OFF", ""),
				"Rart Navn", harFullfoertFjerdeSkoleaar = false, maanedligUtgiftTilsynBarn = 3500
			)
		), annenForsoergerperson = "01123456789"
	)
}

private fun hasTilsynsutgifterBarn(tilleggstonadJsonObj: JsonApplication): Boolean {
	return false
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
		drosjeTransportutgifter = convertDrosjeTransportutgifter(details.kanIkkeReiseKollektivtDagligReise?.oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIPeriodenDuSokerOmStonadFor),
		egenBilTransportutgifter = convertEgenBilTransportutgifter(details.kanIkkeReiseKollektivtDagligReise?.kanBenytteEgenBil),
		aarsakTilIkkeOffentligTransport = convertAarsakTilIkkeOffentligTransport(details.kanIkkeReiseKollektivtDagligReise?.beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt),
		aarsakTilIkkeEgenBil = convertAarsakTilIkkeEgenBil(details.kanIkkeReiseKollektivtDagligReise?.kanIkkeBenytteEgenBilDagligReise?.hvaErArsakenTilAtDuIkkeKanBenytteEgenBil),
		aarsakTilIkkeDrosje = convertAarsakTilIkkeDrosje(details.kanIkkeReiseKollektivtDagligReise?.hvorforKanDuIkkeBenytteDrosje)
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



