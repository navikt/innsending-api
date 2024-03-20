package no.nav.soknad.innsending.util.mapping.tilleggsstonad

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.DokumentSoknadDto


fun convertToJsonTilleggsstonad(soknadDto: DokumentSoknadDto, json: ByteArray?): JsonApplication<JsonTilleggsstonad> {
    if (json == null || json.isEmpty())
        throw BackendErrorException("${soknadDto.innsendingsId}: json fil av søknaden mangler")

    val mapper = jacksonObjectMapper().findAndRegisterModules()
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
        applicationDetails = convertToJsonTilleggsstonad(json.data.data, soknadDto)
    )
}

fun convertToJsonTilleggsstonad(tilleggsstonad: Application, soknadDto: DokumentSoknadDto): JsonTilleggsstonad {
    return JsonTilleggsstonad(
        aktivitetsinformasjon = convertAktivitetsinformasjon(tilleggsstonad),
        maalgruppeinformasjon = convertToJsonMaalgruppeinformasjon(tilleggsstonad),
        rettighetstype = convertToJsonRettighetstyper(tilleggsstonad, soknadDto)

    )
}

private fun convertAktivitetsinformasjon(tilleggsstonad: Application): JsonAktivitetsInformasjon? {
    return if (tilleggsstonad.aktiviteterOgMaalgruppe != null
        && tilleggsstonad.aktiviteterOgMaalgruppe.aktivitet?.aktivitetId != null
    )
        JsonAktivitetsInformasjon(aktivitet = tilleggsstonad.aktiviteterOgMaalgruppe.aktivitet.aktivitetId)
    else
        null
}

fun getMaalgruppeInformasjonFromAktiviteterOgMaalgruppe(aktiviteterOgMaalgruppe: AktiviteterOgMaalgruppe?): JsonMaalgruppeinformasjon? {
    if (aktiviteterOgMaalgruppe == null) return null

    if (aktiviteterOgMaalgruppe.aktivitet?.maalgruppe != null)
        return JsonMaalgruppeinformasjon(
            periode = getAktivitetsPeriode(aktiviteterOgMaalgruppe.aktivitet),
            maalgruppetype = aktiviteterOgMaalgruppe.aktivitet.maalgruppe.maalgruppetype.value
        )
    if (aktiviteterOgMaalgruppe.maalgruppe != null && (aktiviteterOgMaalgruppe.maalgruppe.calculated != null || aktiviteterOgMaalgruppe.maalgruppe.prefilled != null))
        if (aktiviteterOgMaalgruppe.maalgruppe.prefilled?.maalgruppetype != null)
            return JsonMaalgruppeinformasjon(
                periode = getAktivitetsPeriode(aktiviteterOgMaalgruppe.aktivitet),
                maalgruppetype = aktiviteterOgMaalgruppe.maalgruppe.prefilled.maalgruppetype.value
            )
        else if (aktiviteterOgMaalgruppe.maalgruppe.calculated?.maalgruppetype != null)
            return JsonMaalgruppeinformasjon(
                periode = getAktivitetsPeriode(aktiviteterOgMaalgruppe.aktivitet),
                maalgruppetype = aktiviteterOgMaalgruppe.maalgruppe.calculated.maalgruppetype.value
            )

    return null
}

fun getAktivitetsPeriode(aktivitet: Aktivitet?): AktivitetsPeriode? {
    if (aktivitet == null || aktivitet.periode == null) return null

    return AktivitetsPeriode(startdatoDdMmAaaa = aktivitet.periode.fom, sluttdatoDdMmAaaa = aktivitet.periode.tom)
}

fun convertToJsonMaalgruppeinformasjon(tilleggsstonad: Application): JsonMaalgruppeinformasjon? { // TODO

    return getMaalgruppeInformasjonFromAktiviteterOgMaalgruppe(tilleggsstonad.aktiviteterOgMaalgruppe)
        ?: getMaalgruppeinformasjonFromLivssituasjon(tilleggsstonad.flervalg, tilleggsstonad)
}

fun getMaalgruppeinformasjonFromLivssituasjon(
    livssituasjon: Flervalg?,
    tilleggsstonad: Application
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
    if (livssituasjon.regArbSoker == true || "ja".equals(tilleggsstonad.regArbSoker, true)
    )
        return JsonMaalgruppeinformasjon(periode = null, kilde = "BRUKERREGISTRERT", maalgruppetype = "ARBSOKERE")

    // Pri 10
    if (livssituasjon.annet == true || "nei".equals(tilleggsstonad.regArbSoker, true))
        return JsonMaalgruppeinformasjon(
            periode = null,
            kilde = "BRUKERREGISTRERT",
            maalgruppetype = "ANNET"
        )

    return null
}

private fun convertToJsonRettighetstyper(
    tilleggsstonad: Application,
    soknadDto: DokumentSoknadDto
): JsonRettighetstyper {
    return JsonRettighetstyper(
        reise = convertToReisestottesoknad(tilleggsstonad, soknadDto),
        tilsynsutgifter = convertToTilsynsutgifter(tilleggsstonad),
        laeremiddelutgifter = convertToLaeremiddelutgifter(tilleggsstonad),
        bostotte = convertToJsonBostotte(tilleggsstonad),
        flytteutgifter = convertToJsonFlytteutgifter(tilleggsstonad)
    )
}

private fun convertToJsonFlytteutgifter(tilleggsstonad: Application): JsonFlytteutgifter? {
    if (tilleggsstonad.hvorforFlytterDu == null
        //|| tilleggsstonad.startdatoDdMmAaaa == null
        //|| tilleggsstonad.sluttdatoDdMmAaaa == null
        //|| tilleggsstonad.oppgiForsteDagINyJobbDdMmAaaa == null
        || tilleggsstonad.narFlytterDuDdMmAaaa == null
        || tilleggsstonad.farDuDekketUtgifteneDineTilFlyttingPaAnnenMateEnnMedStonadFraNav == null
        || tilleggsstonad.ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra == null
    ) return null

    return JsonFlytteutgifter(
        aktivitetsperiode = JsonPeriode(
            startdatoDdMmAaaa = tilleggsstonad.narFlytterDuDdMmAaaa,
            sluttdatoDdMmAaaa = tilleggsstonad.narFlytterDuDdMmAaaa
        ),
        hvorforFlytterDu = tilleggsstonad.hvorforFlytterDu,
        narFlytterDuDdMmAaaa = tilleggsstonad.narFlytterDuDdMmAaaa,
        oppgiForsteDagINyJobbDdMmAaaa = tilleggsstonad.oppgiForsteDagINyJobbDdMmAaaa,
        erBostedEtterFlytting = tilleggsstonad.detteErAdressenJegSkalBoPaEtterAtJegHarFlyttet != null,
        velgLand1 = tilleggsstonad.velgLand1 ?: VelgLand(label = "Norge", "NO"),
        adresse1 = validateNoneNull(tilleggsstonad.adresse1, "Daglig reise adresse"),
        postnr1 = tilleggsstonad.postnr1,
        farDuDekketUtgifteneDineTilFlyttingPaAnnenMateEnnMedStonadFraNav = tilleggsstonad.farDuDekketUtgifteneDineTilFlyttingPaAnnenMateEnnMedStonadFraNav,
        ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra = tilleggsstonad.ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra,
        jegFlytterSelv = tilleggsstonad.jegFlytterSelv,
        jegVilBrukeFlyttebyra = tilleggsstonad.jegVilBrukeFlyttebyra,
        jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv = tilleggsstonad.jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv
    )
}

private fun convertToJsonBostotte(tilleggsstonad: Application): JsonBostottesoknad? {
    if (tilleggsstonad.hvilkeAdresserHarDuBoutgifterPa == null
        || tilleggsstonad.hvilkeBoutgifterSokerDuOmAFaDekket == null
        || tilleggsstonad.startdatoDdMmAaaa == null
        || tilleggsstonad.sluttdatoDdMmAaaa == null
    ) return null

    return JsonBostottesoknad(
        aktivitetsperiode = JsonPeriode(tilleggsstonad.startdatoDdMmAaaa, tilleggsstonad.sluttdatoDdMmAaaa),
        hvilkeBoutgifterSokerDuOmAFaDekket = tilleggsstonad.hvilkeBoutgifterSokerDuOmAFaDekket,
        bostotteIForbindelseMedSamling = tilleggsstonad.bostotteIForbindelseMedSamling,
        mottarDuBostotteFraKommunen = tilleggsstonad.mottarDuBostotteFraKommunen ?: "Nei", // "Ja" | "Nei"
        bostottebelop = tilleggsstonad.hvorMyeBostotteMottarDu,
        hvilkeAdresserHarDuBoutgifterPa = tilleggsstonad.hvilkeAdresserHarDuBoutgifterPa,
        boutgifterPaHjemstedetMitt = tilleggsstonad.boutgifterPaHjemstedetMitt,
        boutgifterPaAktivitetsadressen = tilleggsstonad.boutgifterPaAktivitetsadressen,
        boutgifterJegHarHattPaHjemstedetMittMenSomHarOpphortIForbindelseMedAktiviteten = tilleggsstonad.boutgifterJegHarHattPaHjemstedetMittMenSomHarOpphortIForbindelseMedAktiviteten,
        erDetMedisinskeForholdSomPavirkerUtgifteneDinePaAktivitetsstedet = tilleggsstonad.erDetMedisinskeForholdSomPavirkerUtgifteneDinePaAktivitetsstedet
    )
}

private fun convertToLaeremiddelutgifter(tilleggsstonad: Application): JsonLaeremiddelutgifter? {
    if (tilleggsstonad.hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore == null || tilleggsstonad.startdatoDdMmAaaa == null || tilleggsstonad.sluttdatoDdMmAaaa == null) return null

    return JsonLaeremiddelutgifter(
        aktivitetsperiode = JsonPeriode(
            tilleggsstonad.startdatoDdMmAaaa,
            tilleggsstonad.sluttdatoDdMmAaaa
        ),
        hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore = tilleggsstonad.hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore,
        hvilketKursEllerAnnenFormForUtdanningSkalDuTa = tilleggsstonad.hvilketKursEllerAnnenFormForUtdanningSkalDuTa,
        oppgiHvorMangeProsentDuStudererEllerGarPaKurs = tilleggsstonad.oppgiHvorMangeProsentDuStudererEllerGarPaKurs
            ?: 0,
        harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler = tilleggsstonad.harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler
            ?: "Nei",
        utgifterTilLaeremidler = tilleggsstonad.utgifterTilLaeremidler ?: 0,
        farDuDekketLaeremidlerEtterAndreOrdninger = tilleggsstonad.farDuDekketLaeremidlerEtterAndreOrdninger ?: "Nei",
        hvorMyeFarDuDekketAvEnAnnenAktor = tilleggsstonad.hvorMyeFarDuDekketAvEnAnnenAktor,
        hvorStortBelopSokerDuOmAFaDekketAvNav = tilleggsstonad.hvorStortBelopSokerDuOmAFaDekketAvNav ?: 0
    )
}

private fun convertToTilsynsutgifter(tilleggsstonad: Application): JsonTilsynsutgifter? {
    if (tilleggsstonad.opplysningerOmBarn == null || tilleggsstonad.startdatoDdMmAaaa == null || tilleggsstonad.sluttdatoDdMmAaaa == null) return null

    return JsonTilsynsutgifter(
        aktivitetsPeriode = JsonPeriode(
            tilleggsstonad.startdatoDdMmAaaa,
            tilleggsstonad.sluttdatoDdMmAaaa
        ),
        barnePass = tilleggsstonad.opplysningerOmBarn.map {
            BarnePass(
                fornavn = it.fornavn,
                etternavn = it.etternavn,
                fodselsdatoDdMmAaaa = validateNoneNull(
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

val reiseDaglig = "NAV 11-12.21B"
val reiseSamling = "NAV 11-12.17B"
val reiseOppstartSlutt = "NAV 11-12.18B"
val reiseArbeid = "NAV 11-12.22B"
val reisestotteskjemaer = listOf(reiseDaglig, reiseSamling, reiseOppstartSlutt, reiseArbeid)
private fun erReisestottesoknad(skjemanr: String): Boolean {
    return reisestotteskjemaer.contains(skjemanr.substring(0, reisestotteskjemaer[0].length))
}


private fun convertToReisestottesoknad(
    tilleggsstonad: Application,
    soknadDto: DokumentSoknadDto
): JsonReisestottesoknad? {
    if (!erReisestottesoknad(soknadDto.skjemanr)) return null
    return JsonReisestottesoknad(
        dagligReise = if (soknadDto.skjemanr.startsWith(reiseDaglig))
            convertToJsonDagligReise(tilleggsstonad) else null,
        reiseSamling = if (soknadDto.skjemanr.startsWith(reiseSamling))
            convertToJsonReiseSamling(tilleggsstonad) else null,
        dagligReiseArbeidssoker = if (soknadDto.skjemanr.startsWith(reiseArbeid))
            convertToJsonReise_Arbeidssoker(tilleggsstonad) else null,
        oppstartOgAvsluttetAktivitet = if (soknadDto.skjemanr.startsWith(reiseOppstartSlutt))
            convertToJsonOppstartOgAvsluttetAktivitet(tilleggsstonad) else null
    )
}

private fun getSelectedDate(userDate: String?, activityDate: String?, field: String): String {
    if (userDate != null && userDate.isNotEmpty()) return userDate
    return validateNoneNull(activityDate, field)
}

private fun convertToJsonDagligReise(tilleggsstonad: Application): JsonDagligReise {
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
        harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde = tilleggsstonad.harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde, // JA | NEI,
        hvorLangReiseveiHarDu = validateNoneNull(
            tilleggsstonad.hvorLangReiseveiHarDu,
            "Daglig reise reisevei"
        ),
        harDuEnReiseveiPaSeksKilometerEllerMer = validateNoneNull(
            tilleggsstonad.harDuEnReiseveiPaSeksKilometerEllerMer,
            "Daglig reise avstand mer enn 6 km"
        ), // JA|NEI
        velgLand1 = tilleggsstonad.velgLand1 ?: VelgLand(label = "Norge", "NO"),
        adresse1 = validateNoneNull(tilleggsstonad.adresse1, "Daglig reise adresse"),
        postnr1 = tilleggsstonad.postnr1,
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
        postnr2 = tilleggsstonad.postnr2,
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
            tilleggsstonad.reiseDato,
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
        velgLandArbeidssoker = tilleggsstonad.velgLandArbeidssoker ?: VelgLand(label = "Norge", "NO"),
        adresse = validateNoneNull(tilleggsstonad.adresse, "Reise arbeidssøker -  adresse mangler"),
        postnr = tilleggsstonad.postnr,
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
            tilleggsstonad.soknadsPeriode?.startdato ?: tilleggsstonad.startdato,
            "Oppstart og avslutning av aktivitet - reisetidspunkt mangler"
        ),
        sluttdatoDdMmAaaa1 = validateNoneNull(
            tilleggsstonad.soknadsPeriode?.sluttdato ?: tilleggsstonad.sluttdato,
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
        velgLand3 = tilleggsstonad.velgLand3 ?: VelgLand(label = "Norge", "NO"),
        adresse3 = validateNoneNull(tilleggsstonad.adresse3, "Oppstart og avslutning av aktivitet -  adresse mangler"),
        postnr3 = tilleggsstonad.postnr3,
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
