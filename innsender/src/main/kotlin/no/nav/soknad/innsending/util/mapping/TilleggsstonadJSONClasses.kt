package no.nav.soknad.innsending.util.mapping

import com.fasterxml.jackson.annotation.JsonProperty

data class Root(
	val language: String,
	val data: Data,
)

data class Data(
	val data: Data2,
	val metadata: Metadata,
	val state: String,
	@JsonProperty("_vnote")
	val vnote: String,
)

data class Data2(
	val hvorforReiserDu: HvorforReiserDu? = null,
	val harDuFlytteutgifter: Boolean? = null, // TODO midlertidig lagt til
	val harDuTilsynsutgifter: Boolean? = null, // TODO midlertidig lagt til
	val harDuBoutgifter: Boolean? = null, // TODO midlertidig lagt til
	val harDuLaeremiddelutgifter: Boolean? = null, // TODO midlertidig lagt til
	val fornavnSoker: String,
	val etternavnSoker: String,
	@JsonProperty("harDuNorskFodselsnummerEllerDNummer")
	val harDuNorskFodselsnummerEllerDnummer: String,
	val annenDokumentasjon: String,
	val harDuNoenTilleggsopplysningerDuMenerErViktigeForSoknadenDin: String,
	val annenPeriode: Map<String, Any>,
	val kanIkkeReiseKollektivtDagligReise: KanIkkeReiseKollektivtDagligReise,
	val startOgSluttdatoForSamlingene: List<StartOgSluttdatoForSamlingene>?,
	val kunEnSamling: Map<String, Any>,
	val kanReiseKollektivt: KanReiseKollektivt?,
	val kanIkkeReiseKollektivtReiseTilSamling: KanIkkeReiseKollektivtReiseTilSamling?,
	val barnSomSkalFlytteMedDeg: List<BarnSomSkalFlytteMedDeg>?,
	val kanIkkeReiseKollektivtOppstartAvslutningHjemreise: KanIkkeReiseKollektivtOppstartAvslutningHjemreise?,
	val harMottattDagpengerSiste6Maneder: HarMottattDagpengerSiste6Maneder,
	val kanIkkeReiseKollektivtArbeidssoker: KanIkkeReiseKollektivtArbeidssoker,
	@JsonProperty("fodselsnummerDNummerSoker")
	val fodselsnummerDnummerSoker: String,
	val hvilkenPeriodeVilDuSokeFor: HvilkenPeriodeVilDuSokeFor,
	val hvorMangeReisedagerHarDuPerUke: Long,
	val harDuEnReiseveiPaSeksKilometerEllerMer: String,
	val hvorLangReiseveiHarDu: Long?,
	val velgLand1: VelgLand1,
	val adresse1: String,
	val kanDuReiseKollektivtDagligReise: String,
	@JsonProperty("hvilkeUtgifterHarDuIForbindelseMedReisenDagligReise")
	val hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise: Long?,
	val skalDuDeltaEllerHarDuDeltattPaFlereSamlinger: String,
	val hvorLangReiseveiHarDu1: Long?,
	val velgLandReiseTilSamling: VelgLandReiseTilSamling,
	val adresse2: String,
	val postnr2: String,
	val kanDuReiseKollektivtReiseTilSamling: String,
	val hvilkenPeriodeVilDuSokeFor1: HvilkenPeriodeVilDuSokeFor1,
	val hvorLangReiseveiHarDu2: Long?,
	val hvorMangeGangerSkalDuReiseEnVei: Long,
	val velgLand3: VelgLand3,
	val adresse3: String,
	val harDuBarnSomSkalFlytteMedDeg: String,
	val harDuBarnSomBorHjemmeOgSomIkkeErFerdigMedFjerdeSkolear: String,
	val harDuSaerligBehovForFlereHjemreiserEnnNevntOvenfor: String,
	val kanDuReiseKollektivtOppstartAvslutningHjemreise: String,
	val reisedatoDdMmAaaa: String,
	val hvorforReiserDuArbeidssoker: String?,
	val dekkerAndreEnnNavEllerDegSelvReisenHeltEllerDelvis: String,
	@JsonProperty("mottarDuEllerHarDuMotattDagpengerILopetAvDeSisteSeksManedene")
	val mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene: String,
	val hvorLangReiseveiHarDu3: Long?,
	val velgLandArbeidssoker: VelgLandArbeidssoker,
	val adresse: String,
	val postnr: String,
	val kanDuReiseKollektivtArbeidssoker: String,
	val bekreftelseForAlleSamlingeneDuSkalDeltaPa: String,
	val bekreftelseForBehovForFlereHjemreiser1: String,
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
	val hentingEllerLeveringAvBarn: Map<String, Any>,
	val annet: Map<String, Any>,
	val kanBenytteEgenBil: Map<String, Any>,
	val kanIkkeBenytteEgenBilDagligReise: Map<String, Any>,
)

data class StartOgSluttdatoForSamlingene(
	val startdatoDdMmAaaa: String,
	val sluttdatoDdMmAaaa: String,
)

data class KanReiseKollektivt(
	@JsonProperty("hvilkeUtgifterHarDuIForbindelseMedReisen1")
	val hvilkeUtgifterHarDuIforbindelseMedReisen1: Long,
	@JsonProperty("hvilkeUtgifterHarDuIForbindelseMedReisen2")
	val hvilkeUtgifterHarDuIforbindelseMedReisen2: Long,
)

data class KanIkkeReiseKollektivtReiseTilSamling(
	val hentingEllerLeveringAvBarn: Map<String, Any>,
	val annet: Map<String, Any>,
	val kanBenytteEgenBil: Map<String, Any>,
	val kanIkkeBenytteEgenBil: Map<String, Any>,
)

data class BarnSomSkalFlytteMedDeg(
	val fornavn: String,
	val etternavn: String,
	val fodselsdatoDdMmAaaa: String,
)

data class KanIkkeReiseKollektivtOppstartAvslutningHjemreise(
	val hentingEllerLeveringAvBarn: Map<String, Any>,
	val annet: Map<String, Any>,
	val kanBenytteEgenBil: KanBenytteEgenBil,
	val kanIkkeBenytteEgenBil: Map<String, Any>,
	val hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt: String,
	val kanDuBenytteEgenBil: String,
)

data class KanBenytteEgenBil(
	val bompenger: Long,
	val piggdekkavgift: Long,
	val ferje: Long,
	val annet: Long,
)

data class HarMottattDagpengerSiste6Maneder(
	val harDuHattForlengetVentetidDeSisteAtteUkene: String,
	val harDuHattTidsbegrensetBortfallDeSisteAtteUkene: String,
)

data class KanIkkeReiseKollektivtArbeidssoker(
	val hentingEllerLeveringAvBarn: HentingEllerLeveringAvBarn,
	val annet: Map<String, Any>,
	val kanBenytteEgenBil: Map<String, Any>,
	val kanIkkeBenytteEgenBil: KanIkkeBenytteEgenBil,
	val hvaErHovedarsakenTilAtDuIkkeKanReiseKollektivt: String,
	val kanDuBenytteEgenBil: String,
)

data class HentingEllerLeveringAvBarn(
	val adressenHvorDuHenterEllerLevererBarn: String,
	val postnr: String,
)

data class KanIkkeBenytteEgenBil(
	val hvaErArsakenTilAtDuIkkeKanBenytteEgenBil: String,
	val kanDuBenytteDrosje: String,
	@JsonProperty("oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIPeriodenDuSokerOmStonadFor")
	val oppgiDenTotaleKostnadenDuHarTilBrukAvDrosjeIperiodenDuSokerOmStonadFor: Long,
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
	val offset: Long,
	val origin: String,
	val referrer: String,
	val browserName: String,
	val userAgent: String,
	val pathName: String,
	val onLine: Boolean,
)
