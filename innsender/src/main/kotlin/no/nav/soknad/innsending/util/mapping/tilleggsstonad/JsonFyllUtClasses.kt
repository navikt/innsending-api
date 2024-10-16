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
	val metadata: Metadata?,
	val state: String?,
	@JsonProperty("_vnote")
	val vnote: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Application(

	// Dine opplysninger blir ikke brukt i forbindelse med konvertering av Json til Xml for tilleggsstønader. Dropper derfor disse propertiene

	// Tilleggsopplysninger
	val tilleggsopplysninger: String? = null,

	// Dersom det er hentet aktivitet / maalgrupper fra Arena skal maalgruppen som har overlappende periode med hentet aktivitet sendes inn.
	val aktiviteterOgMaalgruppe: AktiviteterOgMaalgruppe? = null,

	// Dersom det ikke er registrert maalgrupper i Arena for søker, må søker angi Livssituasjon.
	// Denne skal mappes til en prioritert liste av maalgrupper, der den høyest prioriterte sendes inn.
	// Denne prioriteringen gjøres i fyllut skjemaet og resultatet legges i aktiviteterOgMaalgruppe
	val flervalg: Flervalg? = null,

	// Daglig reise, NAV 11-12.21B
	val soknadsPeriode: SoknadsPeriode? = null,  // Samme som Reise på grunn av oppstart, avslutning eller hjemreise
	val hvorMangeReisedagerHarDuPerUke: Double? = null,
	val harDuEnReiseveiPaSeksKilometerEllerMer: String? = null, // JA|NEI
	val harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde: String? = null, // JA | NEI,
	val hvorLangReiseveiHarDu: Double? = null,
	val velgLand1: VelgLand? = null,
	val adresse1: String? = null,
	val postnr1: String? = null,
	val poststed: String? = null, // Poststed benyttes dersom land = Norge. Merk samme nøkkel brukt i alle tilleggstønadsskjemaene
	val postkode: String? = null, // Postkode benyttes dersom land != Norge. Merk samme nøkkel brukt i alle tilleggstønadsskjemaene
	val kanDuReiseKollektivtDagligReise: String? = null, // ja | nei
	val hvilkeUtgifterHarDuIForbindelseMedReisenDagligReise: Double? = null, // Hvis kanDuReiseKollektivtDagligReise == ja
	val hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt: String? = null,
	val kanIkkeReiseKollektivtDagligReise: KanIkkeReiseKollektivt? = null,

	// Reise til samling, NAV 11-12.17B
	val startOgSluttdatoForSamlingene: List<JsonPeriode>? = null, // hvis skalDuDeltaEllerHarDuDeltattPaFlereSamlinger == Ja
	val hvorLangReiseveiHarDu1: Double? = null,
	val velgLandReiseTilSamling: VelgLand? = null,
	val adresse2: String? = null,
	val postnr2: String? = null,
	//val poststed: String? = null, // Poststed benyttes dersom land = Norge. Merk samme nøkkel brukt i alle tilleggstønadsskjemaene
	//val postkode: String? = null, // Postkode benyttes dersom land != Norge. Merk samme nøkkel brukt i alle tilleggstønadsskjemaene
	val kanDuReiseKollektivtReiseTilSamling: String? = null, // Ja|nei
	val kanReiseKollektivt: KanReiseKollektivt? = null, // hvis kanDuReiseKollektivtReiseTilSamling == ja
	val kanIkkeReiseKollektivtReiseTilSamling: KanIkkeReiseKollektivt? = null, // hvis kanDuReiseKollektivtReiseTilSamling == nei
	val bekreftelseForAlleSamlingeneDuSkalDeltaPa: String? = null,

	// Reise på grunn av oppstart, avslutning eller hjemreise
	//val soknadsPeriode: SoknadsPeriode? = null, NAV 11-12.18B
	val startdato: String? = null,  // Erstattes av soknadsPeriode
	val sluttdato: String? = null,  // Erstattes av soknadsPeriode
	val hvorLangReiseveiHarDu2: Double? = null,
	val hvorMangeGangerSkalDuReiseEnVei: Double? = null,
	val velgLand3: VelgLand? = null,
	val adresse3: String? = null,
	val postnr3: String? = null,
	//val poststed: String? = null, // Poststed benyttes dersom land = Norge. Merk samme nøkkel brukt i alle tilleggstønadsskjemaene
	//val postkode: String? = null, // Postkode benyttes dersom land != Norge. Merk samme nøkkel brukt i alle tilleggstønadsskjemaene
	val harDuBarnSomSkalFlytteMedDeg: String? = null, // ja|nei
	val barnSomSkalFlytteMedDeg: List<BarnSomSkalFlytteMedDeg>? = null,  //hvis harDuBarnSomSkalFlytteMedDeg == ja
	val harDuBarnSomBorHjemmeOgSomIkkeErFerdigMedFjerdeSkolear: String? = null, // ja|nei
	val harDuSaerligBehovForFlereHjemreiserEnnNevntOvenfor: String? = null, // ja|nei
	val bekreftelseForBehovForFlereHjemreiser1: String? = null,
	val kanDuReiseKollektivtOppstartAvslutningHjemreise: String? = null, // ja/nei
	val hvilkeUtgifterHarDuIForbindelseMedReisen4: Double? = null, // hvis kanDuReiseKollektivtOppstartAvslutningHjemreise==ja
	val kanIkkeReiseKollektivtOppstartAvslutningHjemreise: KanIkkeReiseKollektivt? = null, // hvis kanDuReiseKollektivtOppstartAvslutningHjemreise==nei

	// Reise når du er arbeidssøker NAV 11-12.22B
	val regArbSoker: String? = null, // ja || nei
	val reiseDato: String? = null,
	val hvorforReiserDuArbeidssoker: String? = null, // oppfolgingFraNav | jobbintervju | arbeidPaNyttSted
	val dekkerAndreEnnNavEllerDegSelvReisenHeltEllerDelvis: String? = null, // Ja | nei
	val mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene: String? = "nei", // ja|nei
	val harMottattDagpengerSiste6Maneder: HarMottattDagpengerSiste6Maneder? = null, // hvis mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene == ja
	val hvorLangReiseveiHarDu3: Double? = null,
	val velgLandArbeidssoker: VelgLand? = null,
	val adresse: String? = null,
	val postnr: String? = null,
	//val poststed: String? = null, // Poststed benyttes dersom land = Norge. Merk samme nøkkel brukt i alle tilleggstønadsskjemaene
	//val postkode: String? = null, // Postkode benyttes dersom land != Norge. Merk samme nøkkel brukt i alle tilleggstønadsskjemaene

	val kanDuReiseKollektivtArbeidssoker: String? = null, // ja|nei
	val hvilkeUtgifterHarDuIForbindelseMedReisen3: Double? = null, // hvis kanDuReiseKollektivtArbeidssoker==ja?
	val kanIkkeReiseKollektivtArbeidssoker: KanIkkeReiseKollektivt? = null,

	// Flytting, NAV 11-12.23B
	val hvorforFlytterDu: String? = null, // "Jeg flytter fordi jeg har fått ny jobb" | "Jeg flytter i forbindelse med at jeg skal gjennomføre en aktivitet"
	val narFlytterDuDdMmAaaa: String? = null, // 01-01-2023
	val oppgiForsteDagINyJobbDdMmAaaa: String? = null, // 02-01-2023 dersom flytting pga ny jobb
	val detteErAdressenJegSkalBoPaEtterAtJegHarFlyttet: String? = null, // Dette er adressen jeg skal bo på etter at jeg har flyttet
	//val velgLand1: VelgLand?, // Samme som daglig reise
	//val adresse1: String?, // Samme som daglig reise
	//val postnr1: String, // Samme som daglig reise
	//val poststed: String, // Samme som daglig reise. Merk samme nøkkel brukt i alle tilleggstønadsskjemaene
	//val postkode: String? = null, // Postkode benyttes dersom land != Norge. Merk samme nøkkel brukt i alle tilleggstønadsskjemaene
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
	val oppgiHvorMangeProsentDuStudererEllerGarPaKurs: Double? = null, // 0-100
	val harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler: String? = null, // Ja| Nei
	val utgifterTilLaeremidler: Double? = null,
	val farDuDekketLaeremidlerEtterAndreOrdninger: String? = null, // Ja | Nei | Delvis
	val hvorMyeFarDuDekketAvEnAnnenAktor: Double? = null,
	val hvorStortBelopSokerDuOmAFaDekketAvNav: Double? = null,

	// Boutgifter, NAV 11-12.19B
	val startdatoDdMmAaaa: String? = null,
	val sluttdatoDdMmAaaa: String? = null,
	val hvilkeBoutgifterSokerDuOmAFaDekket: String? = null, // "fasteBoutgifter" | "boutgifterIForbindelseMedSamling"
	val bostotteIForbindelseMedSamling: BostotteIForbindelseMedSamling? = null,

	val mottarDuBostotteFraKommunen: String? = null, // "Ja" | "Nei"
	var hvorMyeBostotteMottarDu: Double? = null, // Hvis mottarDuBostotteFraKommunen = Ja
	val hvilkeAdresserHarDuBoutgifterPa: HvilkeAdresserHarDuBoutgifterPa? = null,
	val boutgifterPaAktivitetsadressen: Double? = null,
	val boutgifterPaHjemstedetMitt: Double? = null,
	val boutgifterJegHarHattPaHjemstedetMittMenSomHarOpphortIForbindelseMedAktiviteten: Double? = null,
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

data class SkjemaPeriode( // se også no.nav.soknad.innsending.model.Periode
	val fom: String,
	val tom: String? = null
)

data class BostotteIForbindelseMedSamling(
	val periodeForSamling: List<JsonPeriode>
)

data class HvilkeAdresserHarDuBoutgifterPa(
	val boutgifterPaAktivitetsadressen: Boolean,
	val boutgifterPaHjemstedet: Boolean,
	val boutgifterPaHjemstedetMittSomHarOpphortIForbindelseMedAktiviteten: Boolean
)

data class Drivinglist(
	val selectedVedtaksId: String,
	val tema: String? = null,
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
	val oppgiManedligUtgiftTilBarnepass: Double,
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
	val hvorLangtSkalDuFlytte: Double,
	val hengerleie: Double?,
	val bom: Double?,
	val parkering: Double?,
	val ferje: Double?,
	val annet: Double?
)

data class JegVilBrukeFlyttebyra(
	// Tilbud fra flyttebyrå
	val navnPaFlyttebyra1: String,
	val belop: Double,
	val navnPaFlyttebyra2: String,
	val belop1: Double,
	val jegVelgerABruke: String, // "Flyttebyrå 1" | "Flyttebyrå 2"
	val hvorLangtSkalDuFlytte1: Double
)

data class JegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv(
	val navnPaFlyttebyra1: String,
	val belop: Double,
	val navnPaFlyttebyra2: String,
	val belop1: Double,
	val hvorLangtSkalDuFlytte1: Double,
	val hengerleie: Double?,
	val bom: Double?,
	val parkering: Double?,
	val ferje: Double?,
	val annet: Double?
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
	val hvilkeUtgifterHarDuIForbindelseMedReisen1: Double?
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class KanIkkeReiseKollektivt(
	val hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt: String?, // helsemessigeArsaker | darligTransporttilbud | hentingEllerLeveringAvBarn | annet
	val beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt: String?,
	val hentingEllerLeveringAvBarn: HentingEllerLeveringAvBarn?,
	val annet: AndreArsakerIkkeKollektivt? = null,
	val kanDuBenytteEgenBil: String?, // Ja|Nei
	val kanBenytteEgenBil: KanBenytteEgenBil?,
	val kanIkkeBenytteEgenBil: KanIkkeBenytteEgenBil?,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class AndreArsakerIkkeKollektivt(
	val hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt: String?
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class KanBenytteEgenBil(
	val bompenger: Double?,
	val piggdekkavgift: Double?,
	val ferje: Double?,
	val annet: Double?,
	val vilDuHaUtgifterTilParkeringPaAktivitetsstedet: String?, // Ja |nei
	val parkering: Double?,
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
	val hvaErArsakenTilAtDuIkkeKanBenytteEgenBil: String? = null, // helsemessigeArsaker | disponererIkkeBil | annet
	val hvilkeAndreArsakerGjorAtDuIkkeKanBenytteEgenBil: String?,
	val kanDuBenytteDrosje: String? = null, // ja|nei
	@JsonProperty("oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIPeriodenDuSokerOmStonadFor")
	val oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIperiodenDuSokerOmStonadFor: Double? = null, // hvis kanDuBenytteDrosje==ja
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

