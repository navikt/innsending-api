package no.nav.soknad.innsending.util.mapping.tilleggsstonad

data class JsonApplication<T>(
	val personInfo: JsonPersonInfo? = null,
	val language: String? = null,
	val timezone: String? = null,
	val applicationDetails: T,
)

data class JsonAktivitetsInformasjon(val aktivitet: String? = null)


// Mellom mapping
data class JsonTilleggsstonad(
	val aktivitetsinformasjon: JsonAktivitetsInformasjon? = null,
	val maalgruppeinformasjon: JsonMaalgruppeinformasjon? = null,
	val rettighetstype: JsonRettighetstyper? = null
)

data class JsonPersonInfo(
	val fornavn: String,
	val etternavn: String,
	val ident: PersonIdent,
)

data class PersonIdent(
	val identType: IdentType? = IdentType.PERSONNR,
	val ident: String? = null
)

enum class IdentType { PERSONNR, DNR }

data class JsonRettighetstyper(
	val reise: JsonReisestottesoknad? = null,
	val tilsynsutgifter: JsonTilsynsutgifter? = null,
	val laeremiddelutgifter: JsonLaeremiddelutgifter? = null,
	val bostotte: JsonBostottesoknad? = null,
	val flytteutgifter: JsonFlytteutgifter? = null,
)

data class AktivitetsPeriode(
	val startdatoDdMmAaaa: String,
	val sluttdatoDdMmAaaa: String,
)

data class DestinasjonsAdresse(
	val velgLand1: VelgLand,
	val adresse1: String,
	val postnr1: String?
)

data class JsonFlytteutgifter(
	val aktivitetsperiode: JsonPeriode,
	val hvorforFlytterDu: String, // "Jeg flytter fordi jeg har fått ny jobb" | "Jeg flytter i forbindelse med at jeg skal gjennomføre en aktivitet"
	val narFlytterDuDdMmAaaa: String, // 01-01-2023
	val oppgiForsteDagINyJobbDdMmAaaa: String?, // 02-01-2023 dersom flytting pga ny jobb
	val erBostedEtterFlytting: Boolean,
	val velgLand1: VelgLand,
	val adresse1: String,
	val postnr1: String?,
	val farDuDekketUtgifteneDineTilFlyttingPaAnnenMateEnnMedStonadFraNav: String, // Ja | nei
	val ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra: String, // 	"Jeg flytter selv" | "Jeg vil bruke flyttebyrå" |"Jeg har innhentet tilbud fra minst to flyttebyråer, men velger å flytte selv"
	val jegFlytterSelv: JegFlytterSelv?, // Hvis "Jeg flytter selv"
	val jegVilBrukeFlyttebyra: JegVilBrukeFlyttebyra?, // Hvis "Jeg vil bruke flyttebyrå"
	val jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv: JegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv?,
)

data class JsonBostottesoknad(
	val aktivitetsperiode: JsonPeriode,
	val hvilkeBoutgifterSokerDuOmAFaDekket: String, // "fasteBoutgifter" | "boutgifterIForbindelseMedSamling"
	val bostotteIForbindelseMedSamling: BostotteIForbindelseMedSamling?,

	val mottarDuBostotteFraKommunen: String = "Nei", // "Ja" | "Nei"
	val bostottebelop: Int?,
	val hvilkeAdresserHarDuBoutgifterPa: HvilkeAdresserHarDuBoutgifterPa,
	val boutgifterPaAktivitetsadressen: Int?,
	val boutgifterPaHjemstedetMitt: Int?,
	val boutgifterJegHarHattPaHjemstedetMittMenSomHarOpphortIForbindelseMedAktiviteten: Int?,
	val erDetMedisinskeForholdSomPavirkerUtgifteneDinePaAktivitetsstedet: String?, // "Ja" | "Nei"

)

data class JsonLaeremiddelutgifter(
	val aktivitetsperiode: JsonPeriode,
	val hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore: String, // "videregaendeUtdanning" | "hoyereUtdanning" | "kursEllerAnnenUtdanning"
	val hvilketKursEllerAnnenFormForUtdanningSkalDuTa: String?,
	val oppgiHvorMangeProsentDuStudererEllerGarPaKurs: Int, // 0-100
	val harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler: String, // ja | nei
	val utgifterTilLaeremidler: Int,
	val farDuDekketLaeremidlerEtterAndreOrdninger: String, // ja | nei | delvis
	val hvorMyeFarDuDekketAvEnAnnenAktor: Int?,
	val hvorStortBelopSokerDuOmAFaDekketAvNav: Int
)

data class JsonTilsynsutgifter(
	val aktivitetsPeriode: JsonPeriode,
	val barnePass: List<BarnePass>,
	val fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa: String?,
)

data class BarnePass(
	val fornavn: String,
	val etternavn: String,
	val fodselsdatoDdMmAaaa: String,
	val jegSokerOmStonadTilPassAvDetteBarnet: String?, // "Jeg søker om stønad til pass av dette barnet."
	val sokerStonadForDetteBarnet: SokerStonadForDetteBarnet?
)

data class JsonReisestottesoknad(
	val hvorforReiserDu: HvorforReiserDu? = null,
	val dagligReise: JsonDagligReise? = null,
	val reiseSamling: JsonReiseSamling? = null,
	val dagligReiseArbeidssoker: JsonDagligReiseArbeidssoker? = null,
	val oppstartOgAvsluttetAktivitet: JsonOppstartOgAvsluttetAktivitet? = null,
)

data class JsonDagligReise(
	val startdatoDdMmAaaa: String,
	val sluttdatoDdMmAaaa: String,
	val hvorMangeReisedagerHarDuPerUke: Int?,
	val harDuEnReiseveiPaSeksKilometerEllerMer: String, // JA|NEI
	val harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde: String? = null, // JA | NEI,
	val hvorLangReiseveiHarDu: Int,
	val velgLand1: VelgLand,
	val adresse1: String,
	val postnr1: String?, // Null hvis land != Norge
	val kanDuReiseKollektivtDagligReise: String, // ja | nei
	val hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise: Int?, // Hvis kanDuReiseKollektivtDagligReise == ja
	val hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt: String?,
	val kanIkkeReiseKollektivtDagligReise: KanIkkeReiseKollektivt?
)

data class JsonDagligReiseArbeidssoker(
	val reisedatoDdMmAaaa: String,
	val hvorforReiserDuArbeidssoker: String, // oppfolgingFraNav | jobbintervju | arbeidPaNyttSted
	val dekkerAndreEnnNavEllerDegSelvReisenHeltEllerDelvis: String, // Ja | nei
	val mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene: String, // ja|nei
	val harMottattDagpengerSiste6Maneder: HarMottattDagpengerSiste6Maneder?, // hvis mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene == ja
	val hvorLangReiseveiHarDu3: Int,
	val velgLandArbeidssoker: VelgLand,
	val adresse: String,
	val postnr: String?, // Null hvis land != Norge
	val kanDuReiseKollektivtArbeidssoker: String, // ja|nei
	val hvilkeUtgifterHarDuIForbindelseMedReisen3: Int?, // hvis kanDuReiseKollektivtArbeidssoker==ja?
	val kanIkkeReiseKollektivtArbeidssoker: KanIkkeReiseKollektivt?,
)

data class JsonReiseSamling(
	val startOgSluttdatoForSamlingene: List<JsonPeriode>, // hvis skalDuDeltaEllerHarDuDeltattPaFlereSamlinger == Ja
	val hvorLangReiseveiHarDu1: Int?,
	val velgLandReiseTilSamling: VelgLand,
	val adresse2: String,
	val postnr2: String?, // Null hvis land != Norge
	val kanDuReiseKollektivtReiseTilSamling: String, // Ja|nei
	val kanReiseKollektivt: KanReiseKollektivt?, // hvis kanDuReiseKollektivtReiseTilSamling == ja
	val kanIkkeReiseKollektivtReiseTilSamling: KanIkkeReiseKollektivt?, // hvis kanDuReiseKollektivtReiseTilSamling == nei
	val bekreftelseForAlleSamlingeneDuSkalDeltaPa: String?,
)

data class JsonOppstartOgAvsluttetAktivitet(
	val startdatoDdMmAaaa1: String,
	val sluttdatoDdMmAaaa1: String,
	val hvorLangReiseveiHarDu2: Int?,
	val hvorMangeGangerSkalDuReiseEnVei: Int,
	val velgLand3: VelgLand,
	val adresse3: String,
	val postnr3: String?,  // Null hvis land != Norge
	val harDuBarnSomSkalFlytteMedDeg: String, // ja|nei
	val barnSomSkalFlytteMedDeg: List<BarnSomSkalFlytteMedDeg>?,  //hvis harDuBarnSomSkalFlytteMedDeg == ja
	val harDuBarnSomBorHjemmeOgSomIkkeErFerdigMedFjerdeSkolear: String?, // ja|nei
	val harDuSaerligBehovForFlereHjemreiserEnnNevntOvenfor: String, // ja|nei
	val bekreftelseForBehovForFlereHjemreiser1: String?,
	val kanDuReiseKollektivtOppstartAvslutningHjemreise: String, // ja/nei
	val hvilkeUtgifterHarDuIForbindelseMedReisen4: Int?, // hvis kanDuReiseKollektivtOppstartAvslutningHjemreise==ja
	val kanIkkeReiseKollektivtOppstartAvslutningHjemreise: KanIkkeReiseKollektivt?, // hvis kanDuReiseKollektivtOppstartAvslutningHjemreise==nei
)

data class JsonMaalgruppeinformasjon(
	val periode: AktivitetsPeriode?,
	val kilde: String = "BRUKERREGISTRERT", // f.eks. BRUKERREGISTRERT
	val maalgruppetype: String // f.eks. ENSFORARBS
)

data class Livssituasjon(
	val erDuArbeidssoker: String, // Ja | Nei
	val mottarDuEllerHarDuSoktOmDagpenger: String,  // Ja | Nei
	val mottarDuEllerHarDuSoktOmTiltakspenger: String,  // Ja | Nei
	val gjennomforerDuEnUtdanningSomNavHarGodkjent: String,  // Ja | Nei
	val erDuGjenlevendeEktefelle: String,  // Ja | Nei
	val erDuTidligereFamiliepleier: String,  // Ja | Nei
	val erDuUgiftSkiltEllerSeparertOgErAleneOmOmsorgenForBarn: String,  // Ja | Nei
	val erDuUgiftSkiltEllerSeparertOgErAleneOmOmsorgenForBarn1: String,  // Har du barn under 8 år Ja | Nei.
	val nedsattArbeidsevnePgaSykdom: NedsattArbeidsevnePgaSykdom,
	val annet1: String? // Ingen av valgene ovenfor passer situasjon min
)
