package no.nav.soknad.innsending.util.mapping

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlSchemaType
import javax.xml.datatype.XMLGregorianCalendar

@JacksonXmlRootElement(localName = "Aktivitetsinformasjon")
@JsonPropertyOrder("aktivitetsId")
data class Aktivitetsinformasjon(
	@JacksonXmlProperty(localName = "aktivitetsId")
	val aktivitetsId: String? = ""
)

@JacksonXmlRootElement(localName = "AlternativeTransportutgifter")
@JsonPropertyOrder(
	"kanOffentligTransportBrukes",
	"kanEgenBilBrukes",
	"kollektivTransportutgifter",
	"drosjeTransportutgifter",
	"egenBilTransportutgifter",
	"aarsakTilIkkeOffentligTransport",
	"aarsakTilIkkeEgenBil",
	"aarsakTilIkkeDrosje"
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class AlternativeTransportutgifter(
	@JacksonXmlProperty(localName = "kanOffentligTransportBrukes")
	val kanOffentligTransportBrukes: Boolean? = null,
	@JacksonXmlProperty(localName = "kanEgenBilBrukes")
	val kanEgenBilBrukes: Boolean? = null,
	@JacksonXmlProperty(localName = "kollektivTransportutgifter")
	val kollektivTransportutgifter: KollektivTransportutgifter? = null,
	@JacksonXmlProperty(localName = "drosjeTransportutgifter")
	val drosjeTransportutgifter: DrosjeTransportutgifter? = null,
	@JacksonXmlProperty(localName = "egenBilTransportutgifter")
	val egenBilTransportutgifter: EgenBilTransportutgifter? = null,
	@JacksonXmlProperty(localName = "aarsakTilIkkeOffentligTransport")
	val aarsakTilIkkeOffentligTransport: List<String>? = null,
	@JacksonXmlProperty(localName = "aarsakTilIkkeEgenBil")
	val aarsakTilIkkeEgenBil: List<String>? = null,
	@JacksonXmlProperty(localName = "aarsakTilIkkeDrosje")
	val aarsakTilIkkeDrosje: String? = null
)

@JacksonXmlRootElement(localName = "Anbud")
@JsonPropertyOrder("firmanavn", "tilbudsbeloep")
data class Anbud(
	@JacksonXmlProperty(localName = "firmanavn")
	val firmanavn: String,
	@JacksonXmlProperty(localName = "tilbudsbeloep")
	val tilbudsbeloep: Int
)

@JacksonXmlRootElement(localName = "Barn")
@JsonPropertyOrder(
	"personidentifikator",
	"tilsynskategori",
	"navn",
	"harFullfoertFjerdeSkoleaar",
	"aarsakTilBarnepass",
	"maanedligUtgiftTilsynBarn"
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Barn(
	@JacksonXmlProperty(localName = "personidentifikator")
	@JsonProperty("personidentifikator")
	val personidentifikator: String = "XXXXXX",
	@JsonProperty("tilsynskategori")
	val tilsynskategori: Tilsynskategorier,
	@JsonProperty("navn")
	val navn: String,
	@JsonProperty("harFullfoertFjerdeSkoleaar")
	val harFullfoertFjerdeSkoleaar: Boolean? = null,
	@JsonProperty("aarsakTilBarnepass")
	val aarsakTilBarnepass: List<String>? = null,
	@JsonProperty("maanedligUtgiftTilsynBarn")
	val maanedligUtgiftTilsynBarn: Int
)

@JacksonXmlRootElement(localName = "Boutgifter")
@JsonPropertyOrder(
	"periode",
	"harFasteBoutgifter",
	"harBoutgifterVedSamling",
	"boutgifterAktivitetsted",
	"boutgifterHjemstedAktuell",
	"boutgifterHjemstedOpphoert",
	"boutgifterPgaFunksjonshemminger",
	"mottarBostoette",
	"bostoetteBeloep",
	"samlingsperiode"
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Boutgifter(
	@JacksonXmlProperty(localName = "periode")
	val periode: Periode,
	@JacksonXmlProperty(localName = "harFasteBoutgifter")
	val harFasteBoutgifter: Boolean,
	@JacksonXmlProperty(localName = "harBoutgifterVedSamling")
	val harBoutgifterVedSamling: Boolean,
	@JacksonXmlProperty(localName = "boutgifterAktivitetsted")
	val boutgifterAktivitetsted: Int? = null,
	@JacksonXmlProperty(localName = "boutgifterHjemstedAktuell")
	val boutgifterHjemstedAktuell: Int? = null,
	@JacksonXmlProperty(localName = "boutgifterHjemstedOpphoert")
	val boutgifterHjemstedOpphoert: Int? = null,
	@JacksonXmlProperty(localName = "boutgifterPgaFunksjonshemminger")
	val boutgifterPgaFunksjonshemminger: Boolean? = null,
	@JacksonXmlProperty(localName = "mottarBostoette")
	val mottarBostoette: Boolean,
	@JacksonXmlProperty(localName = "bostoetteBeloep")
	val bostoetteBeloep: Int? = null,
	@JacksonXmlProperty(localName = "samlingsperiode")
	val samlingsperiode: List<Periode>? = null
)

@JacksonXmlRootElement(localName = "DagligReise")
@JsonPropertyOrder(
	"periode",
	"harMedisinskeAarsakerTilTransport",
	"harParkeringsutgift",
	"aktivitetsadresse",
	"avstand",
	"parkeringsutgiftBeloep",
	"innsendingsintervall",
	"alternativeTransportutgifter"
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class DagligReise(
	@JacksonXmlProperty(localName = "periode")
	val periode: Periode,
	@JacksonXmlProperty(localName = "harMedisinskeAarsakerTilTransport")
	val harMedisinskeAarsakerTilTransport: Boolean,
	@JacksonXmlProperty(localName = "harParkeringsutgift")
	val harParkeringsutgift: Boolean? = null,
	@JacksonXmlProperty(localName = "aktivitetsadresse")
	val aktivitetsadresse: String,
	@JacksonXmlProperty(localName = "avstand")
	val avstand: Double,
	@JacksonXmlProperty(localName = "parkeringsutgiftBeloep")
	val parkeringsutgiftBeloep: Int? = null,
	@JacksonXmlProperty(localName = "innsendingsintervall")
	val innsendingsintervall: Innsendingsintervaller? = null,
	@JacksonXmlProperty(localName = "alternativeTransportutgifter")
	val alternativeTransportutgifter: AlternativeTransportutgifter
)

@JacksonXmlRootElement(localName = "EgenBilTransportutgifter")
@JsonPropertyOrder("sumAndreUtgifter")
data class EgenBilTransportutgifter(
	@JacksonXmlProperty(localName = "sumAndreUtgifter")
	val sumAndreUtgifter: Double
)

@JacksonXmlRootElement(localName = "ErUtgifterDekket")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class ErUtgifterDekket(
	@JacksonXmlText
	@JacksonXmlProperty(localName = "value")
	val value: String,  // Ref. enum ErUtgifterDekketKodeverk

	@JacksonXmlProperty(localName = "kodeRef", isAttribute = true)
	val kodeRef: String? = null,

	@JacksonXmlProperty(localName = "kodeverksRef", isAttribute = true)
	val kodeverksRef: String? = "utgifterdekket"
)

@JacksonXmlRootElement(localName = "Flytteutgifter")
@JsonPropertyOrder(
	"flyttingPgaAktivitet",
	"erUtgifterTilFlyttingDekketAvAndreEnnNAV",
	"valgtFlyttebyraa",
	"flytterSelv",
	"flyttingPgaNyStilling",
	"flyttedato",
	"tiltredelsesdato",
	"tilflyttingsadresse",
	"avstand",
	"sumTilleggsutgifter",
	"anbud"
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Flytteutgifter(
	@JacksonXmlProperty(localName = "flyttingPgaAktivitet")
	val flyttingPgaAktivitet: Boolean,
	@JacksonXmlProperty(localName = "erUtgifterTilFlyttingDekketAvAndreEnnNAV")
	val erUtgifterTilFlyttingDekketAvAndreEnnNAV: Boolean,
	@JacksonXmlProperty(localName = "valgtFlyttebyraa")
	val valgtFlyttebyraa: String? = null,
	@JacksonXmlProperty(localName = "flytterSelv")
	val flytterSelv: String? = null,

	@JacksonXmlProperty(localName = "flyttingPgaNyStilling")
	val flyttingPgaNyStilling: Boolean,

	@JacksonXmlProperty(localName = "flyttedato")
	val flyttedato: String,
	@JacksonXmlProperty(localName = "tiltredelsesdato")
	val tiltredelsesdato: XMLGregorianCalendar? = null,
	@JacksonXmlProperty(localName = "tilflyttingsadresse")
	val tilflyttingsadresse: String,
	@JacksonXmlProperty(localName = "avstand")
	val avstand: Int,
	@JacksonXmlProperty(localName = "sumTilleggsutgifter")
	val sumTilleggsutgifter: Double,
	@JacksonXmlProperty(localName = "anbud")
	val anbud: List<Anbud>? = null
)


@JacksonXmlRootElement(localName = "Kodeverdi")
@JsonPropertyOrder("value")
data class Kodeverdi(
	@JacksonXmlProperty(localName = "value")
	val value: String,
	@XmlAttribute(name = "kodeRef")
	@XmlSchemaType(name = "anyURI")
	@JsonProperty("@koderef")
	val kodeRef: String
)

@JacksonXmlRootElement(localName = "Laeremiddelutgifter")
@JsonPropertyOrder(
	"periode",
	"hvorMyeDekkesAvAnnenAktoer",
	"hvorMyeDekkesAvNAV",
	"skolenivaa",
	"prosentandelForUtdanning",
	"beloep",
	"erUtgifterDekket"
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Laeremiddelutgifter(
	@JacksonXmlProperty(localName = "periode")
	val periode: Periode,
	@JacksonXmlProperty(localName = "hvorMyeDekkesAvAnnenAktoer")
	val hvorMyeDekkesAvAnnenAktoer: Double? = null,
	@JacksonXmlProperty(localName = "hvorMyeDekkesAvNAV")
	val hvorMyeDekkesAvNAV: Double? = null,
	@JacksonXmlProperty(localName = "skolenivaa")
	val skolenivaa: Skolenivaaer,
	@JacksonXmlProperty(localName = "prosentandelForUtdanning")
	val prosentandelForUtdanning: Int,
	@JacksonXmlProperty(localName = "beloep")
	val beloep: Int,
	@JacksonXmlProperty(localName = "erUtgifterDekket")
	val erUtgifterDekket: ErUtgifterDekket
)

@JacksonXmlRootElement(localName = "Maalgruppeinformasjon")
@JsonPropertyOrder("periode", "kilde", "maalgruppetype")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Maalgruppeinformasjon(
	@JacksonXmlProperty(localName = "periode")
	@JsonProperty("periode")
	val periode: Periode,

	//@JacksonXmlText
	@JacksonXmlProperty(localName = "kilde")
	@JsonProperty("kilde")
	val kilde: String = "BRUKERREGISTRERT",

	//@JacksonXmlText
	@JsonProperty("maalgruppetype")
	val maalgruppetype: Maalgruppetyper
)

@JacksonXmlRootElement(localName = "Maalgruppetyper")
@JsonPropertyOrder("value")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Maalgruppetyper(
	@JacksonXmlText
	@JacksonXmlProperty(localName = "value")
	val value: String,  // Ref. enum Maalgrupper

	@JacksonXmlProperty(isAttribute = true, localName = "kodeRef")
	protected var kodeRef: String? = null,

	@JacksonXmlProperty(isAttribute = true, localName = "kodeverksRef")
	val kodeverksRef: String? = ""
)

@JacksonXmlRootElement(localName = "Periode")
@JsonPropertyOrder("fom", "tom")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Periode(
	/*
		@XmlElement(required = true)
		@XmlSchemaType(name = "date")
	*/
	@JsonProperty("fom")
	val fom: String,
	/*
		@XmlSchemaType(name = "date")
	*/
	@JsonProperty("tom")
	val tom: String
)

@JacksonXmlRootElement(localName = "Reiseutgifter")
@JsonPropertyOrder(
	"dagligReise",
	"reiseObligatoriskSamling",
	"reiseVedOppstartOgAvsluttetAktivitet",
	"reisestoenadForArbeidssoeker"
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Reiseutgifter(
	@JsonProperty("dagligReise")
	val dagligReise: DagligReise? = null,
	@JsonProperty("reiseObligatoriskSamling")
	val reiseObligatoriskSamling: ReiseObligatoriskSamling? = null,
	@JsonProperty("reisestoenadForArbeidssoeker")
	val reiseVedOppstartOgAvsluttetAktivitet: ReiseVedOppstartOgAvsluttetAktivitet? = null,
	@JsonProperty("reisestoenadForArbeidssoeker")
	val reisestoenadForArbeidssoeker: ReisestoenadForArbeidssoeker? = null
)

@JacksonXmlRootElement(localName = "Rettighetstype")
@JsonPropertyOrder("reiseutgifter", "flytteutgifter", "boutgifter", "laeremiddelutgifter", "tilsynsutgifter")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Rettighetstype(
	@JacksonXmlProperty(localName = "reiseutgifter")
	val reiseutgifter: Reiseutgifter? = null,
	@JacksonXmlProperty(localName = "flytteutgifter")
	val flytteutgifter: Flytteutgifter? = null,
	@JacksonXmlProperty(localName = "boutgifter")
	val boutgifter: Boutgifter? = null,
	@JacksonXmlProperty(localName = "laeremiddelutgifter")
	val laeremiddelutgifter: Laeremiddelutgifter? = null,
	@JacksonXmlProperty(localName = "tilsynsutgifter")
	val tilsynsutgifter: Tilsynsutgifter? = null
)

@JacksonXmlRootElement(localName = "DrosjeTransportutgifter")
@JsonPropertyOrder("beloep")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class DrosjeTransportutgifter(
	@JacksonXmlProperty(localName = "belop")
	val beloep: Int
)

@JacksonXmlRootElement(localName = "Formaal")
@JsonPropertyOrder("value")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Formaal(
	@JacksonXmlText
	@JacksonXmlProperty(localName = "value")
	val value: String,  // Ref. enum FormaalKodeverk

	@JacksonXmlProperty(isAttribute = true, localName = "kodeRef")
	protected var kodeRef: String? = null,

	@JacksonXmlProperty(isAttribute = true, localName = "kodeverksRef")
	val kodeverksRef: String? = ""

)

@JacksonXmlRootElement(localName = "Innsendingsintervaller")
@JsonPropertyOrder("value")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Innsendingsintervaller(
	@JacksonXmlProperty(localName = "value")
	@JacksonXmlText
	val value: String,  // Ref. enum InnsendingsintervallerKodeverk

	@JacksonXmlProperty(isAttribute = true, localName = "kodeRef")
	protected var kodeRef: String? = null,

	@JacksonXmlProperty(isAttribute = true, localName = "kodeverksRef")
	val kodeverksRef: String? = ""
)

@JacksonXmlRootElement(localName = "KollektivTransportutgifter")
@JsonPropertyOrder("beloepPerMaaned")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class KollektivTransportutgifter(
	@JacksonXmlProperty(localName = "beloepPerMaaned")
	val beloepPerMaaned: Int
)

@JacksonXmlRootElement(localName = "ReiseObligatoriskSamling")
@JsonPropertyOrder("periode", "reiseadresser", "avstand", "samlingsperiode", "alternativeTransportutgifter")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class ReiseObligatoriskSamling(
	@JacksonXmlProperty(localName = "periode")
	val periode: Periode,
	@JacksonXmlProperty(localName = "reiseadresser")
	val reiseadresser: String,
	@JacksonXmlProperty(localName = "avstand")
	val avstand: Int,
	@JacksonXmlProperty(localName = "samlingsperiode")
	val samlingsperiode: List<Periode>,
	@JacksonXmlProperty(localName = "alternativeTransportutgifter")
	val alternativeTransportutgifter: AlternativeTransportutgifter
)

@JacksonXmlRootElement(localName = "ReiseOppstartOgAvsluttetAktivitet")
@JsonPropertyOrder(
	"periode",
	"harSaerskilteBehov",
	"aktivitetsstedAdresse",
	"avstand",
	"antallReiser",
	"harBarnUnderFemteklasse",
	"harBarnUnderAtten",
	"alternativeTransportutgifter"
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class ReiseVedOppstartOgAvsluttetAktivitet(
	@JacksonXmlProperty(localName = "periode")
	val periode: Periode,
	@JacksonXmlProperty(localName = "harSaerskilteBehov")
	val harSaerskilteBehov: Boolean? = false,
	@JacksonXmlProperty(localName = "aktivitetsstedAdresse")
	val aktivitetsstedAdresse: String,
	@JacksonXmlProperty(localName = "avstand")
	val avstand: Int,
	@JacksonXmlProperty(localName = "antallReiser")
	val antallReiser: Int,
	@JacksonXmlProperty(localName = "harBarnUnderFemteklasse")
	val harBarnUnderFemteklasse: Boolean,
	@JacksonXmlProperty(localName = "harBarnUnderAtten")
	val harBarnUnderAtten: Boolean?,
	@JacksonXmlProperty(localName = "alternativeTransportutgifter")
	val alternativeTransportutgifter: AlternativeTransportutgifter
)

@JacksonXmlRootElement(localName = "ReisestoenadForArbeidssoeker")
@JsonPropertyOrder(
	"reisedato",
	"harMottattDagpengerSisteSeksMaaneder",
	"formaal",
	"adresse",
	"avstand",
	"erUtgifterDekketAvAndre",
	"erVentetidForlenget",
	"finnesTidsbegrensetbortfall",
	"alternativeTransportutgifter"
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class ReisestoenadForArbeidssoeker(
	@JacksonXmlProperty(localName = "reisedato")
	val reisedato: String,
	@JacksonXmlProperty(localName = "harMottattDagpengerSisteSeksMaaneder")
	val harMottattDagpengerSisteSeksMaaneder: Boolean,
	@JacksonXmlProperty(localName = "formaal")
	val formaal: Formaal,
	@JacksonXmlProperty(localName = "adresse")
	val adresse: String,
	@JacksonXmlProperty(localName = "avstand")
	val avstand: Int,

	@JacksonXmlProperty(localName = "erUtgifterDekketAvAndre")
	val erUtgifterDekketAvAndre: Boolean,

	@JacksonXmlProperty(localName = "erVentetidForlenget")
	val erVentetidForlenget: Boolean,
	@JacksonXmlProperty(localName = "finnesTidsbegrensetbortfall")
	val finnesTidsbegrensetbortfall: Boolean,
	@JacksonXmlProperty(localName = "alternativeTransportutgifter")
	val alternativeTransportutgifter: AlternativeTransportutgifter
)

@JacksonXmlRootElement(localName = "Skolenivaaer")
@JsonPropertyOrder("value")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Skolenivaaer(
	@JsonProperty("value")
	@JacksonXmlText
	@JacksonXmlProperty(localName = "value")
	val value: String, // Ref. enum SkolenivaaerKodeverk

	@JacksonXmlProperty(isAttribute = true, localName = "kodeRef")
	protected var kodeRef: String? = null,

	@JacksonXmlProperty(isAttribute = true, localName = "kodeverksRef")
	val kodeverksRef: String? = "Skolenivaa"
)

@JacksonXmlRootElement(localName = "Tilleggsstoenadsskjema")
@JsonPropertyOrder("aktivitetsinformasjon", "personidentifikator", "maalgruppeinformasjon", "rettighetstype")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Tilleggsstoenadsskjema(
	@JacksonXmlProperty(localName = "aktivitetsinformasjon")
	val aktivitetsinformasjon: Aktivitetsinformasjon,
	@JacksonXmlProperty(localName = "personidentifikator")
	val personidentifikator: String,
	@JacksonXmlProperty(localName = "maalgruppeinformasjon")
	val maalgruppeinformasjon: Maalgruppeinformasjon,
	@JacksonXmlProperty(localName = "rettighetstype")
	val rettighetstype: Rettighetstype
)

@JacksonXmlRootElement(localName = "Tilsynskategorier")
@JsonPropertyOrder("value")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Tilsynskategorier(
	@JacksonXmlText
	@JacksonXmlProperty(localName = "value")
	val value: String,  // Ref. enum TilsynForetasAvKodeverk

	@JacksonXmlProperty(localName = "kodeRef", isAttribute = true)
	protected var kodeRef: String? = null,

	@JacksonXmlProperty(localName = "kodeverksRef", isAttribute = true)
	val kodeverksRef: String? = ""
)

@JacksonXmlRootElement(localName = "Tilsynsutgifter")
@JsonPropertyOrder("tilsynsutgifterBarn", "tilsynsutgifterFamilie")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Tilsynsutgifter(
	//@JsonProperty("tilsynsutgifterBarn")
	@JacksonXmlProperty(localName = "tilsynsutgifterBarn")
	val tilsynsutgifterBarn: TilsynsutgifterBarn? = null,
	//@JsonProperty("tilynsutgifterFamilie")
	@JacksonXmlProperty(localName = "tilynsutgifterFamilie")
	val tilynsutgifterFamilie: TilsynsutgifterFamilie? = null
)

@JacksonXmlRootElement(localName = "TilsynsutgifterBarn")
@JsonPropertyOrder("periode", "barn", "annenForsoergerperson")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class TilsynsutgifterBarn(
	//@JsonProperty("periode")
	@JacksonXmlProperty(localName = "periode")
	val periode: Periode,
	//@JsonProperty("barn")
	@JacksonXmlProperty(localName = "barn")
	val barn: List<Barn>,
	//@JsonProperty("annenForsoergerperson")
	@JacksonXmlProperty(localName = "annenForsoergerperson")
	val annenForsoergerperson: String? = null
)

@JacksonXmlRootElement(localName = "TilsynsutgifterFamilie")
@JsonPropertyOrder(
	"periode",
	"deltTilsyn",
	"annenTilsynsperson",
	"tilsynForetasAv",
	"tilsynsmottaker",
	"maanedligUtgiftTilsynFam"
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class TilsynsutgifterFamilie(
	@JacksonXmlProperty(localName = "periode")
	val periode: Periode,
	@JacksonXmlProperty(localName = "deltTilsyn")
	val deltTilsyn: Boolean,
	@JacksonXmlProperty(localName = "annenTilsynsperson")
	val annenTilsynsperson: String? = null,
	@JacksonXmlProperty(localName = "tilsynForetasAv")
	val tilsynForetasAv: String? = null,
	@JacksonXmlProperty(localName = "tilsynsmottaker")
	val tilsynsmottaker: String,
	@JacksonXmlProperty(localName = "maanedligUtgiftTilsynFam")
	val maanedligUtgiftTilsynFam: Int
)
