package no.nav.soknad.innsending.util.mapping

import com.fasterxml.jackson.annotation.JsonProperty

data class Root(
	val language: String,
	val data: Data,
)

data class Data(
	val data: Application,
	val metadata: Metadata,
	val state: String,
	@JsonProperty("_vnote")
	val vnote: String,
)

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


	val annenDokumentasjon: String,

	// Tilleggsopplysninger
	val harDuNoenTilleggsopplysningerDuMenerErViktigeForSoknadenDin: String,

	// Daglig reise
	val hvilkenPeriodeVilDuSokeFor: HvilkenPeriodeVilDuSokeFor, // TODO HvilkenPeriodeVilDuSokeFor kan ikke benyttes
	val annenPeriode: Map<String, Any>,
	val startdatoDdMmAaaa: String?,
	val sluttdatoDdMmAaaa: String?,
	val hvorMangeReisedagerHarDuPerUke: Int,
	val harDuEnReiseveiPaSeksKilometerEllerMer: String, // JA|NEI
	val harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde: String? = null, // JA | NEI,
	val hvorLangReiseveiHarDu: Int?,
	val velgLand1: VelgLand1,
	val adresse1: String,
	val postnr1: String,
	val kanDuReiseKollektivtDagligReise: String, // ja | nei
	@JsonProperty("hvilkeUtgifterHarDuIForbindelseMedReisenDagligReise")
	val hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise: Int?, // Hvis kanDuReiseKollektivtDagligReise == ja
	val beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt: String?,
	val hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt: String?,
	val kanIkkeReiseKollektivtDagligReise: KanIkkeReiseKollektivtDagligReise?,

	// Reise til samling
	val skalDuDeltaEllerHarDuDeltattPaFlereSamlinger: String?, // ja|nei
	val startOgSluttdatoForSamlingene: List<StartOgSluttdatoForSamlingene>?, // hvis skalDuDeltaEllerHarDuDeltattPaFlereSamlinger == Ja
	val kunEnSamling: StartOgSluttdatoForSamlingene?, // hvis skalDuDeltaEllerHarDuDeltattPaFlereSamlinger == Nei
	val hvorLangReiseveiHarDu1: Int?,
	val velgLandReiseTilSamling: VelgLandReiseTilSamling,
	val adresse2: String,
	val postnr2: String,
	val kanDuReiseKollektivtReiseTilSamling: String, // Ja|nei
	val kanReiseKollektivt: KanReiseKollektivt?, // hvis kanDuReiseKollektivtReiseTilSamling == ja
	val kanIkkeReiseKollektivtReiseTilSamling: KanIkkeReiseKollektivtReiseTilSamling?, // hvis kanDuReiseKollektivtReiseTilSamling == nei
	val bekreftelseForAlleSamlingeneDuSkalDeltaPa: String,

	// Reise på grunn av oppstart, avslutning eller hjemreise
	val hvilkenPeriodeVilDuSokeFor1: HvilkenPeriodeVilDuSokeFor1,
	val hvorLangReiseveiHarDu2: Int?,
	val hvorMangeGangerSkalDuReiseEnVei: Int,
	val velgLand3: VelgLand3,
	val adresse3: String,
	val postnr3: String,
	val harDuBarnSomSkalFlytteMedDeg: String, // ja|nei
	val barnSomSkalFlytteMedDeg: List<BarnSomSkalFlytteMedDeg>?,  //hvis harDuBarnSomSkalFlytteMedDeg == ja
	val harDuBarnSomBorHjemmeOgSomIkkeErFerdigMedFjerdeSkolear: String?, // ja|nei
	val harDuSaerligBehovForFlereHjemreiserEnnNevntOvenfor: String, // ja|nei
	val bekreftelseForBehovForFlereHjemreiser1: String,
	val kanDuReiseKollektivtOppstartAvslutningHjemreise: String, // ja/nei
	val hvilkeUtgifterHarDuIForbindelseMedReisen4: Int?, // hvis kanDuReiseKollektivtOppstartAvslutningHjemreise==ja
	val kanIkkeReiseKollektivtOppstartAvslutningHjemreise: KanIkkeReiseKollektivtOppstartAvslutningHjemreise?, // hvis kanDuReiseKollektivtOppstartAvslutningHjemreise==nei

	// Reise når du er arbeidssøker
	val reisedatoDdMmAaaa: String,
	val hvorforReiserDuArbeidssoker: String?,
	val dekkerAndreEnnNavEllerDegSelvReisenHeltEllerDelvis: String,
	@JsonProperty("mottarDuEllerHarDuMotattDagpengerILopetAvDeSisteSeksManedene")
	val mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene: String, // ja|nei
	val harMottattDagpengerSiste6Maneder: HarMottattDagpengerSiste6Maneder?,
	val hvorLangReiseveiHarDu3: Int?,
	val velgLandArbeidssoker: VelgLandArbeidssoker,
	val adresse: String,
	val postnr: String,
	val kanDuReiseKollektivtArbeidssoker: String, // ja|nei
	val hvilkeUtgifterHarDuIForbindelseMedReisen3: Int?, // hvis kanDuReiseKollektivtArbeidssoker==ja
	val kanIkkeReiseKollektivtArbeidssoker: KanIkkeReiseKollektivtArbeidssoker,

	// Annet som ikke blir brukt
	val legeerklaeringPaMedisinskeArakerTilAtDuIkkeKanReiseKollektivt2: String,
	val dokumentasjonAvReiseutgifter: String,
	@JsonProperty("dokumentasjonAvPlassIBarnehageEllerSkolefritidsordningSfo3")
	val dokumentasjonAvPlassIbarnehageEllerSkolefritidsordningSfo3: String,
	val dokumentasjonAvUtgifterTilDrosje3: String,
)

data class HvorforReiserDu(
	val dagligReise: Boolean,
	val reiseTilSamling: Boolean,
	val reisePaGrunnAvOppstartAvslutningEllerHjemreise: Boolean,
	val reiseNarDuErArbeidssoker: Boolean,
)

data class KanIkkeReiseKollektivtDagligReise(
	val hentingEllerLeveringAvBarn: Map<String, Any>?, // TODO Adresse+Postnr
	val annet: Map<String, Any>,
	val kanDuBenytteEgenBil: String?, // Ja|Nei
	val kanBenytteEgenBil: KanBenytteEgenBil?, // Legge til hvorOfteOnskerDuASendeInnKjoreliste: String? UK|Maned
	val kanIkkeBenytteEgenBilDagligReise: KanIkkeBenytteEgenBil?, // TODO
	val kanDuBenytteDrosje: String?, // Ja|Nei
	val oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIPeriodenDuSokerOmStonadFor: Int?,
	val hvorforKanDuIkkeBenytteDrosje: String?
)

data class StartOgSluttdatoForSamlingene(
	val startdatoDdMmAaaa: String,
	val sluttdatoDdMmAaaa: String,
)

data class KanReiseKollektivt(
	@JsonProperty("hvilkeUtgifterHarDuIForbindelseMedReisen1")
	val hvilkeUtgifterHarDuIforbindelseMedReisen1: Int,
	@JsonProperty("hvilkeUtgifterHarDuIForbindelseMedReisen2")
	val hvilkeUtgifterHarDuIforbindelseMedReisen2: Int,
)

data class KanIkkeReiseKollektivtReiseTilSamling(
	//TODO hva burde sjekkbokser mappes til?
	val hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt: String?,
	val beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt: String?,
	val hentingEllerLeveringAvBarn: HentingEllerLeveringAvBarn? = null,
	val annet: Map<String, Any>? = null, // TODO hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt
	val kanDuBenytteEgenBil: String? = "nei", // ja|nei
	val kanBenytteEgenBil: KanBenytteEgenBil? = null, // hvis kanDuBenytteEgenBil == ja
	val kanIkkeBenytteEgenBil: KanIkkeBenytteEgenBil? = null, // hvis kanDuBenytteEgenBil == nei
)

data class BarnSomSkalFlytteMedDeg(
	val fornavn: String,
	val etternavn: String,
	val fodselsdatoDdMmAaaa: String,
)

data class KanIkkeReiseKollektivtOppstartAvslutningHjemreise(
	val hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt: String?,
	val beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt: String?,
	val hentingEllerLeveringAvBarn: HentingEllerLeveringAvBarn?,
	val annet: Map<String, Any>?,  // TODO hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt
	val kanDuBenytteEgenBil: String, // JA | NEI
	val kanBenytteEgenBil: KanBenytteEgenBil, // hvis kanDuBenytteEgenBil == ja
	val kanIkkeBenytteEgenBil: KanIkkeBenytteEgenBil?, // hvis kanDuBenytteEgenBil == nei
)

data class KanBenytteEgenBil(
	val bompenger: Int?,
	val piggdekkavgift: Int?,
	val ferje: Int?,
	val annet: Int?,
	val oppgiForventetBelopTilParkeringPaAktivitetsstedet: Int?,
	val hvorOfteOnskerDuASendeInnKjoreliste: String? // Ønsker svar UKE | MANED (Jeg ønsker å levere kjøreliste én gang i måneden)
)

data class HarMottattDagpengerSiste6Maneder(
	val harDuHattForlengetVentetidDeSisteAtteUkene: String, // ja|nei
	val harDuHattTidsbegrensetBortfallDeSisteAtteUkene: String, // ja|nei
)

data class KanIkkeReiseKollektivtArbeidssoker(
	val hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt: String?,
	val beskrivDeSpesielleForholdeneVedReiseveienSomGjorAtDuIkkeKanReiseKollektivt: String?,
	val hentingEllerLeveringAvBarn: HentingEllerLeveringAvBarn?,
	val annet: Map<String, Any>, // TODO hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt
	val kanDuBenytteEgenBil: String, // ja|nei
	val kanBenytteEgenBil: KanBenytteEgenBil?,
	val kanIkkeBenytteEgenBil: KanIkkeBenytteEgenBil?
)

data class HentingEllerLeveringAvBarn(
	val adressenHvorDuHenterEllerLevererBarn: String = "",
	val postnr: String = "",
)

data class KanIkkeBenytteEgenBil(
	val hvaErArsakenTilAtDuIkkeKanBenytteEgenBil: String? = null, // TODO mapping sjekkboks eller radioknapper
	val kanDuBenytteDrosje: String? = null, // ja|nei
	@JsonProperty("oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIPeriodenDuSokerOmStonadFor")
	val oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIperiodenDuSokerOmStonadFor: Int? = null, // hvis kanDuBenytteDrosje==ja
	val hvorforKanDuIkkeBenytteDrosje: String? = null// hvis kanDuBenytteDrosje==nei
)

data class HvilkenPeriodeVilDuSokeFor(
	@JsonProperty("110722130823")
	val n110722130823: Boolean,
	@JsonProperty("010124311224")
	val n010124311224: Boolean,
	val annenPeriode: Boolean,
)

data class VelgLand1(
	val label: String,
	val value: String,
)

data class VelgLandReiseTilSamling(
	val label: String,
	val value: String,
)

data class HvilkenPeriodeVilDuSokeFor1(
	@JsonProperty("110722130823")
	val n110722130823: Boolean,
	@JsonProperty("010124311224")
	val n010124311224: Boolean,
	val annenPeriode: Boolean,
)

data class VelgLand3(
	val label: String,
	val value: String,
)

data class VelgLandArbeidssoker(
	val label: String,
	val value: String,
)

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
