package no.nav.soknad.innsending.util.mapping

data class JsonApplication(
	val personInfo: JsonPersonInfo? = null,
	val language: String? = null,
	val timezone: String? = null,
	val tilleggsstonad: JsonTilleggsstonad
)

data class JsonAktivitetsInformasjon(val aktivitet: String? = null)

data class JsonMaalgruppeinformasjon(
	val maalgruppe: String? = null
)


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
	/*
		val bostotte: JsonBostottesoknad? = null,
		val flytteutgifter: JsonFlytteutgifter? = null,
		val laeremiddelutgifter: JsonLaeremiddelutgifter? = null,
		val tilsynsutgifter: JsonTilsynsutgifter? = null
	*/
)

data class JsonReisestottesoknad(
	val hvorforReiserDu: HvorforReiserDu? = null,
	val dagligReise: JsonDagligReise? = null,
	val reiseSamling: JsonReiseSamling? = null,
	val dagligReiseArbeidssoker: JsonDagligReiseArbeidssoker? = null,
	val oppstartOgAvsluttetAktivitet: JsonOppstartOgAvsluttetAktivitet? = null,
)

data class JsonDagligReise(
	val startdatoDdMmAaaa: String?,
	val sluttdatoDdMmAaaa: String?,
	val hvorMangeReisedagerHarDuPerUke: Int?,
	val harDuEnReiseveiPaSeksKilometerEllerMer: String, // JA|NEI
	val harDuAvMedisinskeArsakerBehovForTransportUavhengigAvReisensLengde: String? = null, // JA | NEI,
	val hvorLangReiseveiHarDu: Int,
	val velgLand1: VelgLand1,
	val adresse1: String,
	val postnr1: String,
	val kanDuReiseKollektivtDagligReise: String, // ja | nei
	val hvilkeUtgifterHarDuIforbindelseMedReisenDagligReise: Int?, // Hvis kanDuReiseKollektivtDagligReise == ja
	val hvilkeAndreArsakerErDetSomGjorAtDuIkkeKanReiseKollektivt: String?,
	val kanIkkeReiseKollektivtDagligReise: KanIkkeReiseKollektivtDagligReise?
)

data class JsonDagligReiseArbeidssoker(
	val reisedatoDdMmAaaa: String,
	val hvorforReiserDuArbeidssoker: String, // oppfolgingFraNav | jobbintervju | arbeidPaNyttSted
	val dekkerAndreEnnNavEllerDegSelvReisenHeltEllerDelvis: String, // Ja | nei
	val mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene: String, // ja|nei
	val harMottattDagpengerSiste6Maneder: HarMottattDagpengerSiste6Maneder?, // hvis mottarDuEllerHarDuMotattDagpengerIlopetAvDeSisteSeksManedene == ja
	val hvorLangReiseveiHarDu3: Int,
	val velgLandArbeidssoker: VelgLandArbeidssoker,
	val adresse: String,
	val postnr: String,
	val kanDuReiseKollektivtArbeidssoker: String, // ja|nei
	val hvilkeUtgifterHarDuIForbindelseMedReisen3: Int?, // hvis kanDuReiseKollektivtArbeidssoker==ja?
	val kanIkkeReiseKollektivtArbeidssoker: KanIkkeReiseKollektivtArbeidssoker?,
)

data class JsonReiseSamling(
	val startOgSluttdatoForSamlingene: List<StartOgSluttdatoForSamlingene>, // hvis skalDuDeltaEllerHarDuDeltattPaFlereSamlinger == Ja
	val hvorLangReiseveiHarDu1: Int?,
	val velgLandReiseTilSamling: VelgLandReiseTilSamling,
	val adresse2: String,
	val postnr2: String,
	val kanDuReiseKollektivtReiseTilSamling: String, // Ja|nei
	val kanReiseKollektivt: KanReiseKollektivt?, // hvis kanDuReiseKollektivtReiseTilSamling == ja
	val kanIkkeReiseKollektivtReiseTilSamling: KanIkkeReiseKollektivtReiseTilSamling?, // hvis kanDuReiseKollektivtReiseTilSamling == nei
	val bekreftelseForAlleSamlingeneDuSkalDeltaPa: String?,
)

data class JsonOppstartOgAvsluttetAktivitet(
	val startdatoDdMmAaaa1: String,
	val sluttdatoDdMmAaaa1: String,
	val hvorLangReiseveiHarDu2: Int?,
	val hvorMangeGangerSkalDuReiseEnVei: Int,
	val velgLand3: VelgLand3,
	val adresse3: String,
	val postnr3: String,
	val harDuBarnSomSkalFlytteMedDeg: String, // ja|nei
	val barnSomSkalFlytteMedDeg: List<BarnSomSkalFlytteMedDeg>?,  //hvis harDuBarnSomSkalFlytteMedDeg == ja
	val harDuBarnSomBorHjemmeOgSomIkkeErFerdigMedFjerdeSkolear: String?, // ja|nei
	val harDuSaerligBehovForFlereHjemreiserEnnNevntOvenfor: String, // ja|nei
	val bekreftelseForBehovForFlereHjemreiser1: String?,
	val kanDuReiseKollektivtOppstartAvslutningHjemreise: String, // ja/nei
	val hvilkeUtgifterHarDuIForbindelseMedReisen4: Int?, // hvis kanDuReiseKollektivtOppstartAvslutningHjemreise==ja
	val kanIkkeReiseKollektivtOppstartAvslutningHjemreise: KanIkkeReiseKollektivtOppstartAvslutningHjemreise?, // hvis kanDuReiseKollektivtOppstartAvslutningHjemreise==nei
)
