package no.nav.soknad.innsending.util.mapping.tilleggsstonad

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.soknad.innsending.model.Maalgruppe

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class Root(
	val language: String,
	val data: ApplicationInfo,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApplicationInfo(
	val data: Application,
	val metadata: Metadata,
	val state: String,
	@JsonProperty("_vnote")
	val vnote: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Application(

	// Dine opplysninger
	val fornavnSoker: String,
	val etternavnSoker: String,
	@JsonProperty("harDuNorskFodselsnummerEllerDNummer")
	val harDuNorskFodselsnummerEllerDnummer: String, // ja|nei
	@JsonProperty("fodselsnummerDNummerSoker")
	val fodselsnummerDnummerSoker: String? = null,

	// Tilleggsopplysninger
	val tilleggsopplysninger: String? = null,

	// Dersom det er hentet aktivitet / maalgrupper fra Arena skal maalgruppen som har overlappende periode med hentet aktivitet sendes inn.
	val aktiviteterOgMaalgruppe: AktiviteterOgMaalgruppe? = null,

	// Dersom søker har oppgitt livssituasjon fordi målgruppe mangler
	val flervalg: Flervalg? = null,

	// Dersom det ikke er registrert maalgrupper i Arena for søker, må søker angi Livssituasjon.
	// Denne skal mappes til en prioritert liste av maalgrupper, der den høyest prioriterte sendes inn.
	// Denne prioriteringen gjøres i fyllut skjemaet og resultatet legges i aktiviteterOgMaalgruppe
	// -> TODO Slettes
	val mottarDuEllerHarDuSoktOmDagpenger: String? = null,  // true | false
	val mottarDuEllerHarDuSoktOmTiltakspenger: String? = null,  //  true | false
	val gjennomforerDuEnUtdanningSomNavHarGodkjent: String? = null,  //  true | false
	val erDuGjenlevendeEktefelle: String? = null,  // true | false
	val erDuTidligereFamiliepleier: String? = null,  // true | false
	val erDuUgiftSkiltEllerSeparertOgErAleneOmOmsorgenForBarn: String? = null,  //  true | false
	val erDuUgiftSkiltEllerSeparertOgErAleneOmOmsorgenForBarn1: String? = null,  // Har du barn under 8 år true | false.
	val nedsattArbeidsevnePgaSykdom: NedsattArbeidsevnePgaSykdom? = null,
	val annet1: String? = null, // Ingen av valgene ovenfor passer min situasjon
	// <- TODO Slettes

	// Daglig reise, NAV 11-12.21B
	val soknadsPeriode: SoknadsPeriode? = null,  // Samme som Reise på grunn av oppstart, avslutning eller hjemreise
	val hvorMangeReisedagerHarDuPerUke: Int? = null,
	val harDuEnReiseveiPaSeksKilometerEllerMer: String? = null, // JA|NEI
	val harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde: String? = null, // JA | NEI,
	val hvorLangReiseveiHarDu: Int? = null,
	val velgLand1: VelgLand? = null,
	val adresse1: String? = null,
	val postnr1: String? = null,
	val kanDuReiseKollektivtDagligReise: String? = null, // ja | nei
	val hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise: Int? = null, // Hvis kanDuReiseKollektivtDagligReise == ja
	val hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt: String? = null,
	val kanIkkeReiseKollektivtDagligReise: KanIkkeReiseKollektivt? = null,

	// Reise til samling, NAV 11-12.17B
	val startOgSluttdatoForSamlingene: List<JsonPeriode>? = null, // hvis skalDuDeltaEllerHarDuDeltattPaFlereSamlinger == Ja
	val hvorLangReiseveiHarDu1: Int? = null,
	val velgLandReiseTilSamling: VelgLand? = null,
	val adresse2: String? = null,
	val postnr2: String? = null,
	val kanDuReiseKollektivtReiseTilSamling: String? = null, // Ja|nei
	val kanReiseKollektivt: KanReiseKollektivt? = null, // hvis kanDuReiseKollektivtReiseTilSamling == ja
	val kanIkkeReiseKollektivtReiseTilSamling: KanIkkeReiseKollektivt? = null, // hvis kanDuReiseKollektivtReiseTilSamling == nei
	val bekreftelseForAlleSamlingeneDuSkalDeltaPa: String? = null,

	// Reise på grunn av oppstart, avslutning eller hjemreise
	//val soknadsPeriode: SoknadsPeriode? = null, NAV 11-12.18B
	val startdato: String? = null,  // Erstattes av soknadsPeriode
	val sluttdato: String? = null,  // Erstattes av soknadsPeriode
	val hvorLangReiseveiHarDu2: Int? = null,
	val hvorMangeGangerSkalDuReiseEnVei: Int? = null,
	val velgLand3: VelgLand? = null,
	val adresse3: String? = null,
	val postnr3: String? = null,
	val harDuBarnSomSkalFlytteMedDeg: String? = null, // ja|nei
	val barnSomSkalFlytteMedDeg: List<BarnSomSkalFlytteMedDeg>? = null,  //hvis harDuBarnSomSkalFlytteMedDeg == ja
	val harDuBarnSomBorHjemmeOgSomIkkeErFerdigMedFjerdeSkolear: String? = null, // ja|nei
	val harDuSaerligBehovForFlereHjemreiserEnnNevntOvenfor: String? = null, // ja|nei
	val bekreftelseForBehovForFlereHjemreiser1: String? = null,
	val kanDuReiseKollektivtOppstartAvslutningHjemreise: String? = null, // ja/nei
	val hvilkeUtgifterHarDuIForbindelseMedReisen4: Int? = null, // hvis kanDuReiseKollektivtOppstartAvslutningHjemreise==ja
	val kanIkkeReiseKollektivtOppstartAvslutningHjemreise: KanIkkeReiseKollektivt? = null, // hvis kanDuReiseKollektivtOppstartAvslutningHjemreise==nei

	// Reise når du er arbeidssøker NAV 11-12.22B
	val regArbSoker: String? = null, // ja || nei
	val reiseDato: String? = null,
	val hvorforReiserDuArbeidssoker: String? = null, // oppfolgingFraNav | jobbintervju | arbeidPaNyttSted
	val dekkerAndreEnnNavEllerDegSelvReisenHeltEllerDelvis: String? = null, // Ja | nei
	val mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene: String? = "nei", // ja|nei
	val harMottattDagpengerSiste6Maneder: HarMottattDagpengerSiste6Maneder? = null, // hvis mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene == ja
	val hvorLangReiseveiHarDu3: Int? = null,
	val velgLandArbeidssoker: VelgLand? = null,
	val adresse: String? = null,
	val postnr: String? = null,
	val kanDuReiseKollektivtArbeidssoker: String? = null, // ja|nei
	val hvilkeUtgifterHarDuIForbindelseMedReisen3: Int? = null, // hvis kanDuReiseKollektivtArbeidssoker==ja?
	val kanIkkeReiseKollektivtArbeidssoker: KanIkkeReiseKollektivt? = null,

	// Flytting, NAV 11-12.23B
	val hvorforFlytterDu: String? = null, // "Jeg flytter fordi jeg har fått ny jobb" | "Jeg flytter i forbindelse med at jeg skal gjennomføre en aktivitet"
	val narFlytterDuDdMmAaaa: String? = null, // 01-01-2023
	val oppgiForsteDagINyJobbDdMmAaaa: String? = null, // 02-01-2023 dersom flytting pga ny jobb
	val detteErAdressenJegSkalBoPaEtterAtJegHarFlyttet: String? = null, // Dette er adressen jeg skal bo på etter at jeg har flyttet
	//val velgLand1: VelgLand?, // Samme som daglig reise
	//val adresse1: String?, // Samme som daglig reise
	//val postnr1: String, // Samme som daglig reise
	val farDuDekketUtgifteneDineTilFlyttingPaAnnenMateEnnMedStonadFraNav: String? = null, // Ja | nei
	val ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra: String? = null, // 	"Jeg flytter selv" | "Jeg vil bruke flyttebyrå" |"Jeg har innhentet tilbud fra minst to flyttebyråer, men velger å flytte selv"
	val jegFlytterSelv: JegFlytterSelv? = null, // Hvis "Jeg flytter selv"
	val jegVilBrukeFlyttebyra: JegVilBrukeFlyttebyra? = null, // Hvis "Jeg vil bruke flyttebyrå"
	val jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv: JegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv? = null,

	// Periode for Laeremidler og boutgifter og pass av barn
	//val ikkeRegistrertAktivitetsperiode: JsonPeriode? = null,

	// Laeremidler, NAV 11-12.16B
	// val startdatoDdMmAaaa: String, // Brukes også av boutgifter
	// val sluttdatoDdMmAaaa: String? = null,  // Brukes også av boutgifter
	val hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore: String? = null, // "Jeg skal ta videregående utdanning, eller forkurs på universitet" | "Jeg skal ta utdanning på fagskole, høyskole eller universitet" | "Jeg skal ta kurs eller annen form for utdanning"
	val hvilketKursEllerAnnenFormForUtdanningSkalDuTa: String? = null,
	val oppgiHvorMangeProsentDuStudererEllerGarPaKurs: Int? = null, // 0-100
	val harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler: String? = null, // Ja| Nei
	val utgifterTilLaeremidler: Int? = null,
	val farDuDekketLaeremidlerEtterAndreOrdninger: String? = null, // Ja | Nei | Delvis
	val hvorMyeFarDuDekketAvEnAnnenAktor: Int? = null,
	val hvorStortBelopSokerDuOmAFaDekketAvNav: Int? = null,

	// Boutgifter, NAV 11-12.19B
	val startdatoDdMmAaaa: String? = null,
	val sluttdatoDdMmAaaa: String? = null,
	val hvilkeBoutgifterSokerDuOmAFaDekket: String? = null, // "fasteBoutgifter" | "boutgifterIForbindelseMedSamling"
	val bostotteIForbindelseMedSamling: BostotteIForbindelseMedSamling? = null,

	val mottarDuBostotteFraKommunen: String? = null, // "Ja" | "Nei"
	var hvorMyeBostotteMottarDu: Int? = null, // Hvis mottarDuBostotteFraKommunen = Ja
	val hvilkeAdresserHarDuBoutgifterPa: HvilkeAdresserHarDuBoutgifterPa? = null,
	val boutgifterPaAktivitetsadressen: Int? = null,
	val boutgifterPaHjemstedetMitt: Int? = null,
	val boutgifterJegHarHattPaHjemstedetMittMenSomHarOpphortIForbindelseMedAktiviteten: Int? = null,
	val erDetMedisinskeForholdSomPavirkerUtgifteneDinePaAktivitetsstedet: String? = null, // "Ja" | "Nei"

	// Pass av barn, NAV 11-12.15B
	// val startdatoDdMmAaaa: String? = null, Brukes også av boutgifter
	// val sluttdatoDdMmAaaa: String? = null, Brukes også av boutgifter
	val opplysningerOmBarn: List<OpplysningerOmBarn>? = null,
	val fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa: String? = null, // Skal oppgi fødselsdato
	val fodselsnummerDNummerAndreForelder: String? = null,

	// Kjøreliste
	val drivinglist: Drivinglist? = null,

	)

data class Aktivitet(
	val aktivitetId: String = "ingenAktivitet",
	val maalgruppe: Maalgruppe? = null,
	val periode: SkjemaPeriode? = null,
	val text: String? = ""
)

data class AktiviteterOgMaalgruppe(
	val maalgruppe: MaalgruppeValg? = null,
	val kilde: String? = "BRUKERREGISTRERT",
	val aktivitet: Aktivitet? = null,
)

data class MaalgruppeValg(
	val calculated: Maalgruppe? = null,
	val prefilled: Maalgruppe? = null
)

data class SoknadsPeriode(
	val startdato: String,
	val sluttdato: String
)

data class SkjemaPeriode(
	val fom: String,
	val tom: String
)

data class BostotteIForbindelseMedSamling(
	val periodeForSamling: List<JsonPeriode>
)

data class HvilkeAdresserHarDuBoutgifterPa (
	val boutgifterPaAktivitetsadressen: Boolean,
	val boutgifterPaHjemstedet: Boolean,
	val boutgifterPaHjemstedetMittSomHarOpphortIForbindelseMedAktiviteten: Boolean
)

data class Drivinglist(
	val selectedVedtaksId: String,
	val dates: List<Dates>
)

data class Dates(
	val date: String,
	val parking: String? = null,
	val betalingsplanId: String
)

data class Flervalg(
	val nedsattArbeidsevneInkludererArbeidsavklaringspengerOgUforeftrygd: Boolean? = false,
	val ensligForsorgerOvergangsstonad: Boolean? = false,
	val gjenlevendeEktefelleOmstillingsstonad: Boolean? = false,
	val arbeidssoker: Boolean? = false,
	val aapUforeNedsattArbEvne: Boolean? = false,
	val ensligUtdanning: Boolean? = false,
	val ensligArbSoker: Boolean? = false,
	val gjenlevendeUtdanning: Boolean? = false,
	val gjenlevendeArbSoker: Boolean? = false,
	val tiltakspenger: Boolean? = false,
	val dagpenger: Boolean? = false,
	val regArbSoker: Boolean? = false,
	val tidligereFamiliepleier: Boolean? = false,
	val annet: Boolean? = false
)

data class OpplysningerOmBarn(
	val fornavn: String,
	val etternavn: String,
	val fodselsdatoDdMmAaaa: String?, // TODO skal denne fjernes slik at bare fodselsnummer sendes?
	val fodselsnummerDNummer: String,
	val jegSokerOmStonadTilPassAvDetteBarnet: Boolean?, // "Jeg søker om stønad til pass av dette barnet."
	val sokerStonadForDetteBarnet: SokerStonadForDetteBarnet?
)

data class SokerStonadForDetteBarnet(
	val hvemPasserBarnet: String, // "Barnet mitt får pass av dagmamma eller dagpappa" | "Barnet mitt er i barnehage eller skolefritidsordning (SFO)" | "Barnet mitt har privat ordning for barnepass"
	val oppgiManedligUtgiftTilBarnepass: Int,
	val harBarnetFullfortFjerdeSkolear: String, // "Ja" | "Nei"
	val hvaErArsakenTilAtBarnetDittTrengerPass: String? // Hvis harBarnetFullfortFjerdeSkolear == ja. "Langvarig eller uregelmessig fravær på grunn av arbeid eller utdanning" | "Barnet mitt har et særlig behov for pass" | "Ingen av alternativene passer"
)

data class JsonPeriode(
	val startdatoDdMmAaaa: String,
	val sluttdatoDdMmAaaa: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class Annet(
	val hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt: String?
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class BarnSomSkalFlytteMedDeg(
	val fornavn: String,
	val etternavn: String,
	val fodselsdatoDdMmAaaa: String,
)

data class NedsattArbeidsevnePgaSykdom(
	val harDuNedsattArbeidsevnePaGrunnAvSykdom: String? = null, //  true | false
	val harDuVedtakFraNavOmNedsattArbeidsevnePaGrunnAvSykdom: String? = null, //  true | false
	val mottarDuSykepenger: String? = null, //  true | false
	val mottarDuLonnFraArbeidsgiverMensDuGjennomforerEnAktivitetSomNavHarGodkjent: String? = null //  true | false
)

data class JegFlytterSelv(
	val hvorLangtSkalDuFlytte: Int,
	val hengerleie: Int?,
	val bom: Int?,
	val parkering: Int?,
	val ferje: Int?,
	val annet: Int?
)

data class JegVilBrukeFlyttebyra(
	// Tilbud fra flyttebyrå
	val navnPaFlyttebyra1: String,
	val belop: Int,
	val navnPaFlyttebyra2: String,
	val belop1: Int,
	val jegVelgerABruke: String, // "Flyttebyrå 1" | "Flyttebyrå 2"
	val hvorLangtSkalDuFlytte1: Int
)

data class JegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv(
	val navnPaFlyttebyra1: String,
	val belop: Int,
	val navnPaFlyttebyra2: String,
	val belop1: Int,
	val hvorLangtSkalDuFlytte1: Int,
	val hengerleie: Int?,
	val bom: Int?,
	val parkering: Int?,
	val ferje: Int?,
	val annet: Int?
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class HvorforReiserDu(
	val dagligReise: Boolean,
	val reiseTilSamling: Boolean,
	val reisePaGrunnAvOppstartAvslutningEllerHjemreise: Boolean,
	val reiseNarDuErArbeidssoker: Boolean,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class KanReiseKollektivt(
	val hvilkeUtgifterHarDuIForbindelseMedReisen1: Int?
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class KanIkkeReiseKollektivt(
	val hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt: String?, // helsemessigeArsaker | darligTransporttilbud | hentingEllerLeveringAvBarn | annet
	val beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt: String?,
	val hentingEllerLeveringAvBarn: HentingEllerLeveringAvBarn?,
	val annet: Map<String, Any>?, // TODO skaldet være String?
	val kanDuBenytteEgenBil: String?, // Ja|Nei
	val kanBenytteEgenBil: KanBenytteEgenBil?,
	val kanIkkeBenytteEgenBil: KanIkkeBenytteEgenBil?,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class KanBenytteEgenBil(
	val bompenger: Int?,
	val piggdekkavgift: Int?,
	val ferje: Int?,
	val annet: Int?,
	val vilDuHaUtgifterTilParkeringPaAktivitetsstedet: String?, // Ja |nei
	val parkering: Int?,
	val hvorOfteOnskerDuASendeInnKjoreliste: String? // Ønsker svar UKE | MANED (Jeg ønsker å levere kjøreliste én gang i måneden) jegOnskerALevereKjorelisteEnGangIManeden | jegOnskerALevereKjorelisteEnGangIUken
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class HarMottattDagpengerSiste6Maneder(
	val harDuHattForlengetVentetidDeSisteAtteUkene: String, // ja|nei
	val harDuHattTidsbegrensetBortfallDeSisteAtteUkene: String, // ja|nei
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class HentingEllerLeveringAvBarn(
	val adressenHvorDuHenterEllerLevererBarn: String = "",
	val postnr: String = "",
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class KanIkkeBenytteEgenBil(
	val hvaErArsakenTilAtDuIkkeKanBenytteEgenBil: String? = null, // TODO mapping sjekkboks eller radioknapper? helsemessigeArsaker | disponererIkkeBil | annet
	val hvilkeAndreArsakerGjorAtDuIkkeKanBenytteEgenBil: String?,
	val kanDuBenytteDrosje: String? = null, // ja|nei
	@JsonProperty("oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIPeriodenDuSokerOmStonadFor")
	val oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIperiodenDuSokerOmStonadFor: Int? = null, // hvis kanDuBenytteDrosje==ja
	val hvorforKanDuIkkeBenytteDrosje: String? = null// hvis kanDuBenytteDrosje==nei
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class VelgLand(
	val label: String,
	val value: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class Metadata(
	val timezone: String? = null,
	val offset: Int? = null,
	val origin: String? = null,
	val referrer: String? = null,
	val browserName: String? = null,
	val userAgent: String? = null,
	val pathName: String? = null,
	val onLine: Boolean? = null,
)

