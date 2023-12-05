package no.nav.soknad.innsending.util.mapping

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

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
	val hvorforReiserDu: HvorforReiserDu? = null,
	val harDuFlytteutgifter: Boolean? = null, // TODO midlertidig lagt til
	val harDuTilsynsutgifter: Boolean? = null, // TODO midlertidig lagt til
	val harDuBoutgifter: Boolean? = null, // TODO midlertidig lagt til
	val harDuLaeremiddelutgifter: Boolean? = null, // TODO midlertidig lagt til

	// Dine opplysninger
	val fornavnSoker: String,
	val etternavnSoker: String,
	@JsonProperty("harDuNorskFodselsnummerEllerDNummer")
	val harDuNorskFodselsnummerEllerDnummer: String, // ja|nei
	@JsonProperty("fodselsnummerDNummerSoker")
	val fodselsnummerDnummerSoker: String?,


	val annenDokumentasjon: String?,

	// Tilleggsopplysninger
	val harDuNoenTilleggsopplysningerDuMenerErViktigeForSoknadenDin: String?,
	val tilleggsopplysninger: String?,
	val harRegistrertAktivitetsperiode: String?, // Ja | nei

	// Daglig reise
	@JsonProperty("startdatoDdMmAaaa")
	val startdatoDdMmAaaa: String?,
	val sluttdatoDdMmAaaa: String?,
	val hvorMangeReisedagerHarDuPerUke: Int?,
	val harDuEnReiseveiPaSeksKilometerEllerMer: String, // JA|NEI
	val harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde: String? = null, // JA | NEI,
	val hvorLangReiseveiHarDu: Int?,
	val velgLand1: VelgLand1?,
	val adresse1: String?,
	val postnr1: String?,
	val kanDuReiseKollektivtDagligReise: String?, // ja | nei
	val hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise: Int?, // Hvis kanDuReiseKollektivtDagligReise == ja
	val hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt: String?,
	val kanIkkeReiseKollektivtDagligReise: KanIkkeReiseKollektivtDagligReise?,

	// Reise til samling
	val startOgSluttdatoForSamlingene: List<StartOgSluttdatoForSamlingene>?, // hvis skalDuDeltaEllerHarDuDeltattPaFlereSamlinger == Ja
	val hvorLangReiseveiHarDu1: Int?,
	val velgLandReiseTilSamling: VelgLandReiseTilSamling?,
	val adresse2: String?,
	val postnr2: String?,
	val kanDuReiseKollektivtReiseTilSamling: String?, // Ja|nei
	val kanReiseKollektivt: KanReiseKollektivt?, // hvis kanDuReiseKollektivtReiseTilSamling == ja
	val kanIkkeReiseKollektivtReiseTilSamling: KanIkkeReiseKollektivtReiseTilSamling?, // hvis kanDuReiseKollektivtReiseTilSamling == nei
	val bekreftelseForAlleSamlingeneDuSkalDeltaPa: String?,

	// Reise på grunn av oppstart, avslutning eller hjemreise
	val startdatoDdMmAaaa1: String?,
	val sluttdatoDdMmAaaa1: String?,
	val hvorLangReiseveiHarDu2: Int?,
	val hvorMangeGangerSkalDuReiseEnVei: Int?,
	val velgLand3: VelgLand3?,
	val adresse3: String?,
	val postnr3: String?,
	val harDuBarnSomSkalFlytteMedDeg: String?, // ja|nei
	val barnSomSkalFlytteMedDeg: List<BarnSomSkalFlytteMedDeg>?,  //hvis harDuBarnSomSkalFlytteMedDeg == ja
	val harDuBarnSomBorHjemmeOgSomIkkeErFerdigMedFjerdeSkolear: String?, // ja|nei
	val harDuSaerligBehovForFlereHjemreiserEnnNevntOvenfor: String?, // ja|nei
	val bekreftelseForBehovForFlereHjemreiser1: String?,
	val kanDuReiseKollektivtOppstartAvslutningHjemreise: String?, // ja/nei
	val hvilkeUtgifterHarDuIForbindelseMedReisen4: Int?, // hvis kanDuReiseKollektivtOppstartAvslutningHjemreise==ja
	val kanIkkeReiseKollektivtOppstartAvslutningHjemreise: KanIkkeReiseKollektivtOppstartAvslutningHjemreise?, // hvis kanDuReiseKollektivtOppstartAvslutningHjemreise==nei

	// Reise når du er arbeidssøker
	val reisedatoDdMmAaaa: String?,
	val hvorforReiserDuArbeidssoker: String?, // oppfolgingFraNav | jobbintervju | arbeidPaNyttSted
	val dekkerAndreEnnNavEllerDegSelvReisenHeltEllerDelvis: String?, // Ja | nei
	val mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene: String?, // ja|nei
	val harMottattDagpengerSiste6Maneder: HarMottattDagpengerSiste6Maneder?, // hvis mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene == ja
	val hvorLangReiseveiHarDu3: Int?,
	val velgLandArbeidssoker: VelgLandArbeidssoker?,
	val adresse: String?,
	val postnr: String?,
	val kanDuReiseKollektivtArbeidssoker: String?, // ja|nei
	val hvilkeUtgifterHarDuIForbindelseMedReisen3: Int?, // hvis kanDuReiseKollektivtArbeidssoker==ja?
	val kanIkkeReiseKollektivtArbeidssoker: KanIkkeReiseKollektivtArbeidssoker?,

	// Flytting
	val hvorforFlytterDu: String?, // "Jeg flytter fordi jeg har fått ny jobb" | "Jeg flytter i forbindelse med at jeg skal gjennomføre en aktivitet"
	val narFlytterDuDdMmAaaa: String?, // 01-01-2023
	val oppgiForsteDagINyJobbDdMmAaaa: String?, // 02-01-2023 dersom flytting pga ny jobb
	val farDuDekketUtgifteneDineTilFlyttingPaAnnenMateEnnMedStonadFraNav: String?, // Ja | nei
	val ordnerDuFlyttingenSelvEllerKommerDuTilABrukeFlyttebyra: String?, // 	"Jeg flytter selv" | "Jeg vil bruke flyttebyrå" |"Jeg har innhentet tilbud fra minst to flyttebyråer, men velger å flytte selv"
	val jegFlytterSelv: JegFlytterSelv?, // Hvis "Jeg flytter selv"
	val jegVilBrukeFlyttebyra: JegVilBrukeFlyttebyra?, // Hvis "Jeg vil bruke flyttebyrå"
	val jegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv: JegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv?,

	// Periode for Laeremidler og boutgifter og pass av barn
	val ikkeRegistrertAktivitetsperiode: IkkeRegistrertAktivitetsperiode?,

	// Laeremidler
	val hvilkenTypeUtdanningEllerOpplaeringSkalDuGjennomfore: String?, // "Jeg skal ta videregående utdanning, eller forkurs på universitet" | "Jeg skal ta utdanning på fagskole, høyskole eller universitet" | "Jeg skal ta kurs eller annen form for utdanning"
	val hvilketKursEllerAnnenFormForUtdanningSkalDuTa: String?,
	val oppgiHvorMangeProsentDuStudererEllerGarPaKurs: Int?, // 0-100
	val harDuEnFunksjonshemningSomGirDegStorreUtgifterTilLaeremidler: String?, // Ja| Nei
	val utgifterTilLaeremidler: Int?,
	val farDuDekketLaeremidlerEtterAndreOrdninger: String?, // Ja | Nei | Delvis
	val hvorMyeFarDuDekketAvEnAnnenAktor: Int?,
	val hvorStortBelopSokerDuOmAFaDekketAvNav: Int?,

	// Boutgifter
	val hvilkeBoutgifterSokerDuOmAFaDekket: String?, // "Jeg søker om å få dekket faste boutgifter" | "Jeg søker om å få dekket boutgifter i forbindelse med samling"
	val bostotteIForbindelseMedSamling: List<PeriodeForSamling>?,

	val mottarDuBostotteFraKommunen: String?, // "Ja" | "Nei"
	val hvilkeAdresserHarDuBoutgifterPa: List<String>?, // "Jeg har boutgifter på aktivitetsadressen min" | "Jeg har fortsatt boutgifter på hjemstedet mitt" | "Jeg har hatt boutgifter på hjemstedet mitt, som har opphørt i forbindelse med aktiviteten"
	val boutgifterPaAktivitetsadressen: Int?,
	val boutgifterJegHarHattPaHjemstedetMittMenSomHarOpphortIForbindelseMedAktiviteten: Int?,
	val erDetMedisinskeForholdSomPavirkerUtgifteneDinePaAktivitetsstedet: String?, // "Ja" | "Nei"

	// Pass av barn
	val datagrid: List<Datagrid>?,
	val fodselsdatoTilDenAndreForelderenAvBarnetDdMmAaaa: String?,


	// Annet som ikke blir brukt
	val legeerklaeringPaMedisinskeArakerTilAtDuIkkeKanReiseKollektivt2: String?,
	val dokumentasjonAvReiseutgifter: String?,
	val dokumentasjonAvPlassIbarnehageEllerSkolefritidsordningSfo3: String?,
	val dokumentasjonAvUtgifterTilDrosje3: String?,
)

data class Datagrid(
	val fornavn: String,
	val etternavn: String,
	val fodselsdatoDdMmAaaa: String,
	val jegSokerOmStonadTilPassAvDetteBarnet: String?, // "Jeg søker om stønad til pass av dette barnet."
	val sokerStonadForDetteBarnet: SokerStonadForDetteBarnet?
)

data class SokerStonadForDetteBarnet(
	val hvemPasserBarnet: String, // "Barnet mitt får pass av dagmamma eller dagpappa" | "Barnet mitt er i barnehage eller skolefritidsordning (SFO)" | "Barnet mitt har privat ordning for barnepass"
	val oppgiManedligUtgiftTilBarnepass: Int,
	val harBarnetFullfortFjerdeSkolear: String, // "Ja" | "Nei"
	val hvaErArsakenTilAtBarnetDittTrengerPass: String? // Hvis harBarnetFullfortFjerdeSkolear == ja. "Langvarig eller uregelmessig fravær på grunn av arbeid eller utdanning" | "Barnet mitt har et særlig behov for pass" | "Ingen av alternativene passer"
)

data class PeriodeForSamling(
	val startdatoDdMmAaaa: String,
	val sluttdatoDdMmAaaa: String
)

data class IkkeRegistrertAktivitetsperiode(
	val startdatoDdMmAaaa: String,
	val sluttdatoDdMmAaaa: String,
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
	val jegVelgerABruke: String // "Flyttebyrå 1" | "Flyttebyrå 2"
)

data class JegHarInnhentetTilbudFraMinstToFlyttebyraerMenVelgerAFlytteSelv(
	val navnPaFlyttebyra1: String,
	val belop: Int,
	val navnPaFlyttebyra2: String,
	val belop1: Int,
	val hvorLangtSkalDuFlytte: Int,
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
data class KanIkkeReiseKollektivtDagligReise(
	val hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt: String?, // helsemessigeArsaker | darligTransporttilbud | hentingEllerLeveringAvBarn | annet
	val beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt: String?,
	val hentingEllerLeveringAvBarn: HentingEllerLeveringAvBarn?,
	val annet: Map<String, Any>?,
	val kanDuBenytteEgenBil: String?, // Ja|Nei
	val kanBenytteEgenBil: KanBenytteEgenBil?,
	val kanIkkeBenytteEgenBilDagligReise: KanIkkeBenytteEgenBil?,
	val kanDuBenytteDrosje: String?, // Ja|Nei
	val oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIPeriodenDuSokerOmStonadFor: Int?,
	val hvorforKanDuIkkeBenytteDrosje: String?
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class StartOgSluttdatoForSamlingene(
	val startdatoDdMmAaaa: String,
	val sluttdatoDdMmAaaa: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class KanReiseKollektivt(
	val hvilkeUtgifterHarDuIForbindelseMedReisen1: Int?
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class KanIkkeReiseKollektivtReiseTilSamling(
	//TODO hva burde sjekkbokser mappes til?
	val hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt: String?, // helsemessigeArsaker |darligTransporttilbud|hentingEllerLeveringAvBarn|annet
	val beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt: String? = null,
	val hentingEllerLeveringAvBarn: HentingEllerLeveringAvBarn? = null,
	val annet: Annet? = null, // TODO hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt
	val kanDuBenytteEgenBil: String? = "nei", // ja|nei
	val kanBenytteEgenBil: KanBenytteEgenBil? = null, // hvis kanDuBenytteEgenBil == ja
	val kanIkkeBenytteEgenBil: KanIkkeBenytteEgenBil? = null, // hvis kanDuBenytteEgenBil == nei
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class BarnSomSkalFlytteMedDeg(
	val fornavn: String,
	val etternavn: String,
	val fodselsdatoDdMmAaaa: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class Annet(
	val hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt: String?
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class KanIkkeReiseKollektivtOppstartAvslutningHjemreise(
	val hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt: String?,
	val beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt: String?,
	val hentingEllerLeveringAvBarn: HentingEllerLeveringAvBarn?,
	val annet: Map<String, Any>?,  // TODO hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt
	val kanDuBenytteEgenBil: String, // JA | NEI
	val kanBenytteEgenBil: KanBenytteEgenBil, // hvis kanDuBenytteEgenBil == ja
	val kanIkkeBenytteEgenBil: KanIkkeBenytteEgenBil?, // hvis kanDuBenytteEgenBil == nei
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class KanBenytteEgenBil(
	val bompenger: Int?,
	val piggdekkavgift: Int?,
	val ferje: Int?,
	val annet: Int?,
	val vilDuHaUtgifterTilParkeringPaAktivitetsstedet: String?, // Ja |nei
	val oppgiForventetBelopTilParkeringPaAktivitetsstedet: Int?,
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
data class KanIkkeReiseKollektivtArbeidssoker(
	val hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt: String?,
	val beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt: String?,
	val hentingEllerLeveringAvBarn: HentingEllerLeveringAvBarn?,
	val annet: Map<String, Any>, // TODO hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt
	val kanDuBenytteEgenBil: String, // ja|nei
	val kanBenytteEgenBil: KanBenytteEgenBil?,
	val kanIkkeBenytteEgenBil: KanIkkeBenytteEgenBil?
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
data class HvilkenPeriodeVilDuSokeFor(
	val fom: String,
	val tom: String
)

data class HvilkenPeriodeVilDuSokeFor1(
	val fom: String,
	val tom: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class VelgLand1(
	val label: String,
	val value: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class VelgLandReiseTilSamling(
	val label: String,
	val value: String,
)


@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class VelgLand3(
	val label: String,
	val value: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class VelgLandArbeidssoker(
	val label: String,
	val value: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class Metadata(
	val timezone: String,
	val offset: Int,
	val origin: String,
	val referrer: String,
	val browserName: String,
	val userAgent: String,
	val pathName: String,
	val onLine: Boolean,
)
