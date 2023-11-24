package no.nav.soknad.innsending.util.mapping

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule
import com.google.gson.Gson
import no.nav.soknad.innsending.model.DokumentSoknadDto
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.*
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar


fun json2Xml(soknadDto: DokumentSoknadDto, jsonFil: ByteArray?): ByteArray {
	// Konverter json-string til tilleggsstonadJson
	if (jsonFil == null || jsonFil.isEmpty()) throw RuntimeException("${soknadDto.innsendingsId}: Ingen json fil av søknaden lastet opp")
	val jsonFilString = jsonFil.toString(Charsets.UTF_8)
	val gson = Gson()
	val tilleggstonadJsonObj = gson.fromJson(jsonFilString, Root::class.java)

	// Map tilleggsstonadFraJson til tilleggsstonadXML
	val tilleggsstonadXmlObj = convertToTilleggsstonadsskjema(soknadDto, tilleggstonadJsonObj)

	// Konverter tilleggsstonadXML til xml-string
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
			fom = convertToXMLGregorianCalendar("2023-12-01"), tom = convertToXMLGregorianCalendar("2024-01-31")
		),
		kilde = "BRUKERREGISTRERT",
		maalgruppetype = Maalgruppetyper(value = "NEDSARBEVN", kodeverksRef = "NEDSARBEVN")
	) // TODO
}

private fun convertToXMLGregorianCalendar(date: String): XMLGregorianCalendar {
	val format = "yyyy-MM-dd"
	val cal = GregorianCalendar(TimeZone.getDefault())
	cal.setTime(SimpleDateFormat(format).parse(date))
	return DatatypeFactory.newInstance().newXMLGregorianCalendar(cal)
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
			fom = convertToXMLGregorianCalendar("2023-12-01+01:00"),
			tom = convertToXMLGregorianCalendar("2024-03-30+01:00")
		),
		harFasteBoutgifter = true,
		harBoutgifterVedSamling = false,
		boutgifterHjemstedAktuell = BigInteger.valueOf(8000),
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
		prosentandelForUtdanning = "100".toBigInteger(),
		beloep = "2000".toBigInteger(),
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
		flyttedato = convertToXMLGregorianCalendar("2023-12-24"),
		tilflyttingsadresse = SammensattAdresse(
			land = null,
			adresse = "Kongens Gate 1",
			postnr = "3701"
		).sammensattAdresse,
		avstand = "130".toBigInteger(),
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
			fom = convertToXMLGregorianCalendar("2023-12-01+01:00"),
			tom = convertToXMLGregorianCalendar("2024-03-30+01:00")
		), barn = listOf(
			Barn(
				personidentifikator = "12345678901", tilsynskategori = Tilsynskategorier("KOM", ""),
				"Morsomt Navn", harFullfoertFjerdeSkoleaar = false, maanedligUtgiftTilsynBarn = BigInteger.valueOf(4500L)
			),
			Barn(
				personidentifikator = "12345678902", tilsynskategori = Tilsynskategorier("OFF", ""),
				"Rart Navn", harFullfoertFjerdeSkoleaar = false, maanedligUtgiftTilsynBarn = BigInteger.valueOf(3500L)
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
			fom = convertToXMLGregorianCalendar("2023-12-01"), // TODO
			tom = convertToXMLGregorianCalendar("2024-03-30")  // TODO
		),
		deltTilsyn = true,
		annenTilsynsperson = "Annen Person",
		tilsynForetasAv = "Annen Person",
		tilsynsmottaker = "Rart Barn",
		maanedligUtgiftTilsynFam = "3500".toBigInteger()
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
		periode = convertPeriode("2023-10-01+02:00", "2024-01-31+01:00") // TODO erstatt med hvilkenPeriodeVilDuSokeFor
		,
		aktivitetsadresse = SammensattAdresse(
			land = details.velgLand1.label,
			adresse = details.adresse1,
			details.postnr
		).sammensattAdresse,
		avstand = details.hvorLangReiseveiHarDu.toDouble(),
		harMedisinskeAarsakerTilTransport = !"JA".equals(details.kanDuReiseKollektivtDagligReise),
		alternativeTransportutgifter = convertAlternativeTransportutgifter(details),
		innsendingsintervall = convertInnsendingsintervaller(null),
		harParkeringsutgift = null,
		parkeringsutgiftBeloep = null
	)
}

private fun convertReisestoenadForArbeidssoeker(tilleggstonadJsonObj: Root): ReisestoenadForArbeidssoeker? {
	val details = tilleggstonadJsonObj.data.data
	if (details.hvorforReiserDuArbeidssoker == null || details.hvorLangReiseveiHarDu3 == null) return null

	return ReisestoenadForArbeidssoeker(
		reisedato = convertToXMLGregorianCalendar(date = details.reisedatoDdMmAaaa),
		harMottattDagpengerSisteSeksMaaneder = "JA".equals(
			details.mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene,
			true
		),
		formaal = Formaal(value = details.hvorforReiserDuArbeidssoker),
		adresse = SammensattAdresse(
			land = details.velgLandArbeidssoker.label,
			adresse = details.adresse,
			postnr = details.postnr
		).sammensattAdresse,
		avstand = details.hvorLangReiseveiHarDu3.toBigInteger(),
		erUtgifterDekketAvAndre = "JA".equals(details.dekkerAndreEnnNavEllerDegSelvReisenHeltEllerDelvis, true),
		erVentetidForlenget = false // TODO
		,
		finnesTidsbegrensetbortfall = false //TODO
		,
		alternativeTransportutgifter = AlternativeTransportutgifter(

		)

	)
}


private fun convertReiseVedOppstartOgAvsluttetAktivitet(tilleggstonadJsonObj: Root): ReiseVedOppstartOgAvsluttetAktivitet? {
	val details = tilleggstonadJsonObj.data.data
	if (details.hvorLangReiseveiHarDu2 == null) return null

	return ReiseVedOppstartOgAvsluttetAktivitet(
		periode = convertPeriode("2022-07-11", "2023-08-13") // TODO Bruke hvilkenPeriodeVilDuSokeFor1?
		,
		aktivitetsstedAdresse = SammensattAdresse(
			land = details.velgLand3.label,
			adresse = details.adresse3,
			postnr = ""
		).sammensattAdresse,
		avstand = details.hvorLangReiseveiHarDu2.toBigInteger(),
		antallReiser = BigInteger.valueOf(details.hvorMangeGangerSkalDuReiseEnVei),
		harBarnUnderFemteklasse = "JA".equals(details.harDuBarnSomBorHjemmeOgSomIkkeErFerdigMedFjerdeSkolear, true),
		harBarnUnderAtten = null // TODO mangler i eksempelet
		,
		alternativeTransportutgifter = AlternativeTransportutgifter(
			kanOffentligTransportBrukes = "JA".equals(details.kanDuReiseKollektivtOppstartAvslutningHjemreise, true),
			kanEgenBilBrukes = !"JA".equals(
				details.kanDuReiseKollektivtOppstartAvslutningHjemreise,
				true
			)  // TODO mangler info "Kan du benytte egen bil"
			,
			kollektivTransportutgifter = convertKollektivTransportutgifter(details.hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise),
			drosjeTransportutgifter = convertDrosjeTransportutgifter(null)  // TODO mangler info "Kan du benytte drosje" og detaljering av kost
			,
			egenBilTransportutgifter = convertEgenBilTransportutgifter(null) // TODO mangler detaljering av kost
			,
			aarsakTilIkkeOffentligTransport = convertAarsakTilIkkeOffentligTransport(null) // TODO
			,
			aarsakTilIkkeEgenBil = convertAarsakTilIkkeEgenBil(null) // TODO mangler eksempel
			,
			aarsakTilIkkeDrosje = null // TODO mangler eksempel
		)

	)

}


private fun convertReiseObligatoriskSamling(tilleggstonadJsonObj: Root): ReiseObligatoriskSamling? {
	val details = tilleggstonadJsonObj.data.data
	if (details.startOgSluttdatoForSamlingene == null
		|| details.startOgSluttdatoForSamlingene.isEmpty()
		|| details.hvorLangReiseveiHarDu1 == null
	) return null

	val periodeList = createPeriodeList(details.startOgSluttdatoForSamlingene)

	val startOfPeriods = periodeList.map { it.fom.toGregorianCalendar().timeInMillis }.min()
	val endOfPeriods = periodeList.map { it.tom.toGregorianCalendar().timeInMillis }.max()
	val startDate = GregorianCalendar()
	startDate.setTimeInMillis(startOfPeriods)
	val endDate = GregorianCalendar()
	endDate.setTimeInMillis(endOfPeriods)

	return ReiseObligatoriskSamling(
		periode = Periode(
			fom = DatatypeFactory.newInstance().newXMLGregorianCalendar(startDate),
			tom = DatatypeFactory.newInstance().newXMLGregorianCalendar(endDate)
		),
		reiseadresser = SammensattAdresse(
			land = details.velgLandReiseTilSamling.label,
			adresse = details.adresse2,
			postnr = details.postnr
		).sammensattAdresse,
		avstand = details.hvorLangReiseveiHarDu1.toBigInteger(),
		samlingsperiode = periodeList,
		alternativeTransportutgifter = AlternativeTransportutgifter(
			kanOffentligTransportBrukes = details.kanReiseKollektivt != null,
			kollektivTransportutgifter = convertKollektivTransportutgifter(
				details.kanReiseKollektivt?.hvilkeUtgifterHarDuIforbindelseMedReisen1
					?: 0 // TODO Trenger ikke spesifisering av utgift pr reise
			) // TODO burde vært liste med utgift pr samling?
			,
			kanEgenBilBrukes = details.kanReiseKollektivt == null // TODO bytt med egen property
			,
			drosjeTransportutgifter = null // TODO
			,
			egenBilTransportutgifter = null // TODO
			,
			aarsakTilIkkeOffentligTransport = null // TODO
			,
			aarsakTilIkkeEgenBil = null // TODO
			,
			aarsakTilIkkeDrosje = null // TODO
		)

	)
}

private fun createPeriodeList(fromJsonPeriodes: List<StartOgSluttdatoForSamlingene>): List<Periode> {
	return fromJsonPeriodes.map { convertPeriode(fom = it.startdatoDdMmAaaa, tom = it.sluttdatoDdMmAaaa) }.toList()
}

private fun convertPeriode(fom: String, tom: String): Periode {
	return Periode(fom = convertToXMLGregorianCalendar(fom), tom = convertToXMLGregorianCalendar(tom))
// TODO bruker startOgSluttdatoForSamlingene da dette er eneste fornuftige i json eksempelet, HvilkenPeriodeVilDuSokeFor kan ikke benyttes.
// TODO vi må bli enig om korrekt mapping av data format. I "fasit skal det være YYYY-MM-DD+HH:MM (kooreksjon i forhold til GMT)
}

private fun convertAdresse(adresse: String, postnummer: String, landKode: String): String {
	return adresse + ", " + postnummer + ", " + landKode // TODO uklar mapping av felter.
}

private fun convertAlternativeTransportutgifter(details: Data2): AlternativeTransportutgifter {
	return AlternativeTransportutgifter(
		kanOffentligTransportBrukes = "JA".equals(details.kanDuReiseKollektivtDagligReise, true),
		kanEgenBilBrukes = !"JA".equals(
			details.kanDuReiseKollektivtDagligReise,
			true
		)  // TODO kan være at søker ikke har bil å må ha annen transport
		,
		kollektivTransportutgifter = convertKollektivTransportutgifter(details.hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise),
		drosjeTransportutgifter = convertDrosjeTransportutgifter(null)  // TODO
		,
		egenBilTransportutgifter = convertEgenBilTransportutgifter(null) // TODO
		,
		aarsakTilIkkeOffentligTransport = convertAarsakTilIkkeOffentligTransport(null) // TODO
		,
		aarsakTilIkkeEgenBil = convertAarsakTilIkkeEgenBil(null) // TODO
		,
		aarsakTilIkkeDrosje = convertAarsakTilIkkeDrosje(null) // TODO
	)
}

private fun convertKollektivTransportutgifter(utgifter: Long?): KollektivTransportutgifter? {
	if (utgifter == null) return null
	return KollektivTransportutgifter(beloepPerMaaned = utgifter.toBigInteger())
}

private fun convertDrosjeTransportutgifter(utgifter: Long?): DrosjeTransportutgifter? {
	if (utgifter == null) return null
	return DrosjeTransportutgifter(beloep = utgifter.toBigInteger())
}

private fun convertInnsendingsintervaller(details: Data2?): Innsendingsintervaller? {
	if (details == null) return null

	return Innsendingsintervaller(value = "UKE") // TODO kan være UKE | MND, se InnsendingsintervallerKodeverk
}

private fun convertEgenBilTransportutgifter(utgifter: Long?): EgenBilTransportutgifter? {
	if (utgifter == null) return null
	return EgenBilTransportutgifter(sumAndreUtgifter = utgifter.toDouble()) // TODO Summere utgift til bompenger, parkering, piggdekk, ferge, annet
}

private fun convertAarsakTilIkkeOffentligTransport(aarsakTilIkkeOffentligTransport: String?): List<String>? {
	if (aarsakTilIkkeOffentligTransport == null) return null
	return listOf(aarsakTilIkkeOffentligTransport)
}

private fun convertAarsakTilIkkeEgenBil(aarsak: String?): List<String>? {
	if (aarsak == null) return null
	return listOf(aarsak)
}

private fun convertAarsakTilIkkeDrosje(aarsak: String?): String? {
	if (aarsak.isNullOrBlank()) return null
	return aarsak
}



