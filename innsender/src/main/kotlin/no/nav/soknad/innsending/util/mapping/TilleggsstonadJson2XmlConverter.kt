package no.nav.soknad.innsending.util.mapping

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.soknad.innsending.exceptions.BackendErrorException
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

fun convertToJson(soknadDto: DokumentSoknadDto, json: ByteArray?): Root {
	if (json == null || json.isEmpty())
		throw BackendErrorException("${soknadDto.innsendingsId}: Ingen json fil av søknaden mangler")

	val mapper = jacksonObjectMapper()
	return mapper.readValue(json, Root::class.java)
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

fun convertToTilleggsstonadsskjema(soknadDto: DokumentSoknadDto, tilleggstonadJsonObj: Root): Tilleggsstoenadsskjema {
	return Tilleggsstoenadsskjema(
		aktivitetsinformasjon = convertToAktivitetsinformasjon(tilleggstonadJsonObj),
		personidentifikator = soknadDto.brukerId,
		maalgruppeinformasjon = convertToMaalgruppeinformasjon(tilleggstonadJsonObj),
		rettighetstype = convertToRettighetstype(soknadDto, tilleggstonadJsonObj)
	)

}

fun convertToAktivitetsinformasjon(tilleggstonadJsonObj: Root): Aktivitetsinformasjon {
	return Aktivitetsinformasjon("123456") // TODO
}

fun convertToMaalgruppeinformasjon(tilleggstonadJsonObj: Root): Maalgruppeinformasjon {
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

fun convertToRettighetstype(soknadDto: DokumentSoknadDto, tilleggstonadJsonObj: Root): Rettighetstype {
	return Rettighetstype(
		boutgifter = convertBostotte(tilleggstonadJsonObj),
		laeremiddelutgifter = convertLaremiddler(tilleggstonadJsonObj),
		flytteutgifter = convertFlytteutgifter(tilleggstonadJsonObj),
		tilsynsutgifter = convertTilsynsutgifter(tilleggstonadJsonObj),
		reiseutgifter = convertReiseUtgifter(tilleggstonadJsonObj)
	)
}

fun convertBostotte(tilleggstonadJsonObj: Root): Boutgifter? {
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

private fun hasBoUtgifter(tilleggstonadJsonObj: Root): Boolean {
	val details = tilleggstonadJsonObj.data.data
	return details.harDuBoutgifter == true
}

fun convertLaremiddler(tilleggstonadJsonObj: Root): Laeremiddelutgifter? {
	if (!hasLaeremiddelutgifter(tilleggstonadJsonObj)) return null
	val details = tilleggstonadJsonObj.data.data

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

private fun hasLaeremiddelutgifter(tilleggstonadJsonObj: Root): Boolean {
	val details = tilleggstonadJsonObj.data.data
	return details.harDuLaeremiddelutgifter == true
}

fun convertFlytteutgifter(tilleggstonadJsonObj: Root): Flytteutgifter? {
	if (tilleggstonadJsonObj.data.data.harDuFlytteutgifter != true) return null

	val details = tilleggstonadJsonObj.data.data

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

fun convertTilsynsutgifter(tilleggstonadJsonObj: Root): Tilsynsutgifter? {
	if (tilleggstonadJsonObj.data.data.harDuTilsynsutgifter != true) return null

	val utgifterBarn = convertTilsynsutgifterBarn(tilleggstonadJsonObj)
	val utgifterFamilie = convertFamilieutgifter(tilleggstonadJsonObj)

	if (utgifterBarn == null && utgifterFamilie == null) return null

	return Tilsynsutgifter(tilsynsutgifterBarn = utgifterBarn, tilynsutgifterFamilie = utgifterFamilie)
}

private fun convertTilsynsutgifterBarn(tilleggstonadJsonObj: Root): TilsynsutgifterBarn? {
	if (tilleggstonadJsonObj.data.data.harDuTilsynsutgifter != true) return null

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

private fun convertFamilieutgifter(tilleggstonadJsonObj: Root): TilsynsutgifterFamilie? {
	if (tilleggstonadJsonObj.data.data.harDuTilsynsutgifter != true) return null

	val details = tilleggstonadJsonObj.data.data

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

fun convertReiseUtgifter(tilleggstonadJsonObj: Root): Reiseutgifter? {
	if (!hasReiseutgifter(tilleggstonadJsonObj.data.data.hvorforReiserDu)) return null

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

private fun convertDagligReise(tilleggstonadJsonObj: Root): DagligReise? {
	val details = tilleggstonadJsonObj.data.data
	val hasTravelExpenses = details.hvorforReiserDu
	if (hasTravelExpenses == null || !hasTravelExpenses.dagligReise) return null
	if (details.hvorLangReiseveiHarDu == null) return null

	return DagligReise(
		periode = convertPeriode("2023-10-01+02:00", "2024-01-31+01:00"), // TODO erstatt med hvilkenPeriodeVilDuSokeFor
		aktivitetsadresse = SammensattAdresse(
			land = details.velgLand1.label,
			adresse = details.adresse1,
			details.postnr
		).sammensattAdresse,
		avstand = details.hvorLangReiseveiHarDu.toDouble(),
		harMedisinskeAarsakerTilTransport = convertToBoolean(details.kanDuReiseKollektivtDagligReise) ?: false,
		alternativeTransportutgifter = convertAlternativeTransportutgifter_DagligReise(details),
		innsendingsintervall = convertInnsendingsintervaller(details.kanIkkeReiseKollektivtDagligReise?.kanBenytteEgenBil?.hvorOfteOnskerDuASendeInnKjoreliste),
		harParkeringsutgift = convertToBoolean(details.kanIkkeReiseKollektivtDagligReise?.kanDuBenytteEgenBil) ?: false && details.kanIkkeReiseKollektivtDagligReise?.kanBenytteEgenBil?.oppgiForventetBelopTilParkeringPaAktivitetsstedet ?: 0 > 0,
		parkeringsutgiftBeloep = details.kanIkkeReiseKollektivtDagligReise?.kanBenytteEgenBil?.oppgiForventetBelopTilParkeringPaAktivitetsstedet
	)
}

private fun convertReisestoenadForArbeidssoeker(tilleggstonadJsonObj: Root): ReisestoenadForArbeidssoeker? {
	val details = tilleggstonadJsonObj.data.data
	if (details.hvorforReiserDuArbeidssoker == null || details.hvorLangReiseveiHarDu3 == null) return null

	return ReisestoenadForArbeidssoeker(
		reisedato = convertToDateStringWithTimeZone(date = details.reisedatoDdMmAaaa),
		harMottattDagpengerSisteSeksMaaneder = convertToBoolean(details.mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene)
			?: false,
		formaal = Formaal(value = details.hvorforReiserDuArbeidssoker),
		adresse = SammensattAdresse(
			land = details.velgLandArbeidssoker.label,
			adresse = details.adresse,
			postnr = details.postnr
		).sammensattAdresse,
		avstand = details.hvorLangReiseveiHarDu3,
		erUtgifterDekketAvAndre = convertToBoolean(details.dekkerAndreEnnNavEllerDegSelvReisenHeltEllerDelvis) ?: false,
		erVentetidForlenget = convertToBoolean(details.harMottattDagpengerSiste6Maneder?.harDuHattForlengetVentetidDeSisteAtteUkene)
			?: false,
		finnesTidsbegrensetbortfall = convertToBoolean(details.harMottattDagpengerSiste6Maneder?.harDuHattTidsbegrensetBortfallDeSisteAtteUkene)
			?: false,
		alternativeTransportutgifter = AlternativeTransportutgifter(
			kanOffentligTransportBrukes = convertToBoolean(details.kanDuReiseKollektivtArbeidssoker),
			kollektivTransportutgifter = convertKollektivTransportutgifter(details.hvilkeUtgifterHarDuIForbindelseMedReisen3),
			aarsakTilIkkeOffentligTransport = convertAarsakTilIkkeOffentligTransport(details.kanIkkeReiseKollektivtArbeidssoker.hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt),
			kanEgenBilBrukes = convertToBoolean(details.kanIkkeReiseKollektivtArbeidssoker.kanDuBenytteEgenBil),
			egenBilTransportutgifter = convertEgenBilTransportutgifter(details.kanIkkeReiseKollektivtArbeidssoker.kanBenytteEgenBil),
			drosjeTransportutgifter = convertDrosjeTransportutgifter(details.kanIkkeReiseKollektivtArbeidssoker.kanIkkeBenytteEgenBil?.oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIperiodenDuSokerOmStonadFor),
			aarsakTilIkkeDrosje = convertAarsakTilIkkeDrosje(details.kanIkkeReiseKollektivtArbeidssoker.kanIkkeBenytteEgenBil?.hvorforKanDuIkkeBenytteDrosje)
		)

	)
}


private fun convertReiseVedOppstartOgAvsluttetAktivitet(tilleggstonadJsonObj: Root): ReiseVedOppstartOgAvsluttetAktivitet? {
	val details = tilleggstonadJsonObj.data.data
	if (details.hvorLangReiseveiHarDu2 == null) return null

	return ReiseVedOppstartOgAvsluttetAktivitet(
		periode = convertPeriode("2022-07-11", "2023-08-13") // TODO Bruke HvilkenPeriodeVilDuSokeFor1 når den er ok
		,
		aktivitetsstedAdresse = SammensattAdresse(
			land = details.velgLand3.label,
			adresse = details.adresse3,
			postnr = ""
		).sammensattAdresse,
		avstand = details.hvorLangReiseveiHarDu2,
		antallReiser = details.hvorMangeGangerSkalDuReiseEnVei,
		harBarnUnderFemteklasse = convertToBoolean(details.harDuBarnSomBorHjemmeOgSomIkkeErFerdigMedFjerdeSkolear) ?: false,
		harBarnUnderAtten = null // TODO mangler i eksempelet
		,
		alternativeTransportutgifter = AlternativeTransportutgifter(
			kanOffentligTransportBrukes = convertToBoolean(details.kanDuReiseKollektivtOppstartAvslutningHjemreise),
			kanEgenBilBrukes = convertToBoolean(details.kanIkkeReiseKollektivtOppstartAvslutningHjemreise?.kanDuBenytteEgenBil),
			kollektivTransportutgifter = convertKollektivTransportutgifter(details.hvilkeUtgifterHarDuIForbindelseMedReisen4),
			drosjeTransportutgifter = convertDrosjeTransportutgifter(details.kanIkkeReiseKollektivtOppstartAvslutningHjemreise?.kanIkkeBenytteEgenBil?.oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIperiodenDuSokerOmStonadFor),
			egenBilTransportutgifter = convertEgenBilTransportutgifter(details.kanIkkeReiseKollektivtOppstartAvslutningHjemreise?.kanBenytteEgenBil),
			aarsakTilIkkeOffentligTransport = convertAarsakTilIkkeOffentligTransport(details.kanIkkeReiseKollektivtOppstartAvslutningHjemreise?.beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt),
			aarsakTilIkkeEgenBil = convertAarsakTilIkkeEgenBil(details.kanIkkeReiseKollektivtOppstartAvslutningHjemreise?.kanIkkeBenytteEgenBil?.hvaErArsakenTilAtDuIkkeKanBenytteEgenBil),
			aarsakTilIkkeDrosje = details.kanIkkeReiseKollektivtOppstartAvslutningHjemreise?.kanIkkeBenytteEgenBil?.hvorforKanDuIkkeBenytteDrosje
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

private fun convertReiseObligatoriskSamling(tilleggstonadJsonObj: Root): ReiseObligatoriskSamling? {
	val details = tilleggstonadJsonObj.data.data
	val deltaPaSamling = convertToBoolean(details.skalDuDeltaEllerHarDuDeltattPaFlereSamlinger)
	if (deltaPaSamling == null ||
		details.startOgSluttdatoForSamlingene == null || details.startOgSluttdatoForSamlingene.isEmpty() ||
		details.hvorLangReiseveiHarDu1 == null
	) return null

	val periodeList = createPeriodeList(details.startOgSluttdatoForSamlingene)

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
			land = details.velgLandReiseTilSamling.label,
			adresse = details.adresse2,
			postnr = details.postnr2
		).sammensattAdresse,
		avstand = details.hvorLangReiseveiHarDu1,
		samlingsperiode = periodeList,
		alternativeTransportutgifter = AlternativeTransportutgifter(
			kanOffentligTransportBrukes = convertToBoolean(details.kanDuReiseKollektivtReiseTilSamling),
			// TODO Trenger ikke spesifisering av utgift pr reise
			// TODO burde vært liste med utgift pr samling?
			kollektivTransportutgifter = convertKollektivTransportutgifter(
				details.kanReiseKollektivt?.hvilkeUtgifterHarDuIforbindelseMedReisen1
			),
			kanEgenBilBrukes = convertToBoolean(details.kanIkkeReiseKollektivtReiseTilSamling?.kanDuBenytteEgenBil),
			egenBilTransportutgifter = convertEgenBilTransportutgifter(details.kanIkkeReiseKollektivtReiseTilSamling?.kanBenytteEgenBil),
			drosjeTransportutgifter = convertDrosjeTransportutgifter(details.kanIkkeReiseKollektivtReiseTilSamling?.kanIkkeBenytteEgenBil?.oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIperiodenDuSokerOmStonadFor),
			aarsakTilIkkeOffentligTransport = convertAarsakTilIkkeOffentligTransport(details.kanIkkeReiseKollektivtReiseTilSamling?.hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt),
			aarsakTilIkkeEgenBil = convertAarsakTilIkkeEgenBil(details.kanIkkeReiseKollektivtReiseTilSamling?.kanIkkeBenytteEgenBil?.hvaErArsakenTilAtDuIkkeKanBenytteEgenBil),
			aarsakTilIkkeDrosje = convertAarsakTilIkkeDrosje(details.kanIkkeReiseKollektivtReiseTilSamling?.kanIkkeBenytteEgenBil?.hvorforKanDuIkkeBenytteDrosje)
		)

	)
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

private fun convertAlternativeTransportutgifter_DagligReise(details: Application): AlternativeTransportutgifter {
	val kanBenytteEgenBil = convertToBoolean(details.kanIkkeReiseKollektivtDagligReise?.kanDuBenytteEgenBil) ?: false
	return AlternativeTransportutgifter(
		kanOffentligTransportBrukes = convertToBoolean(details.kanDuReiseKollektivtDagligReise),
		kanEgenBilBrukes = kanBenytteEgenBil,
		kollektivTransportutgifter = convertKollektivTransportutgifter(details.hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise),
		drosjeTransportutgifter = convertDrosjeTransportutgifter(details.kanIkkeReiseKollektivtDagligReise?.oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIPeriodenDuSokerOmStonadFor),
		egenBilTransportutgifter = convertEgenBilTransportutgifter(details.kanIkkeReiseKollektivtDagligReise?.kanBenytteEgenBil),
		aarsakTilIkkeOffentligTransport = convertAarsakTilIkkeOffentligTransport(details.beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt),
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



