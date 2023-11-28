package no.nav.soknad.innsending.util.mapping

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText
import javax.xml.bind.annotation.*
import javax.xml.datatype.XMLGregorianCalendar

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Aktivitetsinformasjon", propOrder = ["aktivitetsId"])
data class Aktivitetsinformasjon(
	@XmlElement(required = true)
	val aktivitetsId: String = ""
)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
	name = "AlternativeTransportutgifter",
	propOrder = ["kanOffentligTransportBrukes", "kanEgenBilBrukes", "kollektivTransportutgifter", "drosjeTransportutgifter", "egenBilTransportutgifter", "aarsakTilIkkeOffentligTransport", "aarsakTilIkkeEgenBil", "aarsakTilIkkeDrosje"]
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class AlternativeTransportutgifter(
	val kanOffentligTransportBrukes: Boolean? = null,
	val kanEgenBilBrukes: Boolean? = null,
	val kollektivTransportutgifter: KollektivTransportutgifter? = null,
	val drosjeTransportutgifter: DrosjeTransportutgifter? = null,
	val egenBilTransportutgifter: EgenBilTransportutgifter? = null,
	val aarsakTilIkkeOffentligTransport: List<String>? = null,
	val aarsakTilIkkeEgenBil: List<String>? = null,
	val aarsakTilIkkeDrosje: String? = null
)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Anbud", propOrder = ["firmanavn", "tilbudsbeloep"])
data class Anbud(
	val firmanavn: String,
	val tilbudsbeloep: Int
)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
	name = "Barn",
	propOrder = ["personidentifikator", "tilsynskategori", "navn", "harFullfoertFjerdeSkoleaar", "aarsakTilBarnepass", "maanedligUtgiftTilsynBarn"]
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Barn(
	val personidentifikator: String,
	val tilsynskategori: Tilsynskategorier,
	val navn: String,
	val harFullfoertFjerdeSkoleaar: Boolean? = null,
	val aarsakTilBarnepass: List<String>? = null,
	val maanedligUtgiftTilsynBarn: Int
)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
	name = "Boutgifter",
	propOrder = ["periode", "harFasteBoutgifter", "harBoutgifterVedSamling", "boutgifterAktivitetsted", "boutgifterHjemstedAktuell", "boutgifterHjemstedOpphoert", "boutgifterPgaFunksjonshemminger", "mottarBostoette", "bostoetteBeloep", "samlingsperiode"]
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Boutgifter(
	val periode: Periode,
	val harFasteBoutgifter: Boolean,
	val harBoutgifterVedSamling: Boolean,
	val boutgifterAktivitetsted: Int? = null,
	val boutgifterHjemstedAktuell: Int? = null,
	val boutgifterHjemstedOpphoert: Int? = null,
	val boutgifterPgaFunksjonshemminger: Boolean? = null,
	val mottarBostoette: Boolean,
	val bostoetteBeloep: Int? = null,
	val samlingsperiode: List<Periode>? = null
)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
	name = "DagligReise",
	propOrder = ["periode", "harMedisinskeAarsakerTilTransport", "harParkeringsutgift", "aktivitetsadresse", "avstand", "parkeringsutgiftBeloep", "innsendingsintervall", "alternativeTransportutgifter"]
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class DagligReise(
	val periode: Periode,
	val harMedisinskeAarsakerTilTransport: Boolean,
	val harParkeringsutgift: Boolean? = null,
	val aktivitetsadresse: String,
	val avstand: Double,
	val parkeringsutgiftBeloep: Int? = null,
	val innsendingsintervall: Innsendingsintervaller? = null,
	val alternativeTransportutgifter: AlternativeTransportutgifter
)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "EgenBilTransportutgifter", propOrder = ["sumAndreUtgifter"])
data class EgenBilTransportutgifter(
	val sumAndreUtgifter: Double
)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ErUtgifterDekket")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class ErUtgifterDekket(
	@XmlValue
	@JacksonXmlText
	val value: String,  // Ref. enum ErUtgifterDekketKodeverk

	@XmlAttribute(name = "kodeRef")
	@XmlSchemaType(name = "anyURI")
	protected var kodeRef: String? = null,

	@XmlAttribute(name = "kodeverksRef")
	@XmlSchemaType(name = "anyURI")
	val kodeverksRef: String? = ""
)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
	name = "Flytteutgifter",
	propOrder = ["flyttingPgaAktivitet", "erUtgifterTilFlyttingDekketAvAndreEnnNAV", "valgtFlyttebyraa", "flytterSelv", "flyttingPgaNyStilling", "flyttedato", "tiltredelsesdato", "tilflyttingsadresse", "avstand", "sumTilleggsutgifter", "anbud"]
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Flytteutgifter(
	val flyttingPgaAktivitet: Boolean,
	val erUtgifterTilFlyttingDekketAvAndreEnnNAV: Boolean,
	val valgtFlyttebyraa: String? = null,
	val flytterSelv: String? = null,

	val flyttingPgaNyStilling: Boolean,

	val flyttedato: String,
	val tiltredelsesdato: XMLGregorianCalendar? = null,
	val tilflyttingsadresse: String,
	val avstand: Int,
	val sumTilleggsutgifter: Double,
	val anbud: List<Anbud>? = null
)


@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Kodeverdi", propOrder = ["value"])
data class Kodeverdi(
	@XmlValue
	val value: String,
	@XmlAttribute(name = "kodeRef")
	@XmlSchemaType(name = "anyURI")
	val kodeRef: String
)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
	name = "Laeremiddelutgifter",
	propOrder = ["periode", "hvorMyeDekkesAvAnnenAktoer", "hvorMyeDekkesAvNAV", "skolenivaa", "prosentandelForUtdanning", "beloep", "erUtgifterDekket"]
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Laeremiddelutgifter(
	val periode: Periode,
	val hvorMyeDekkesAvAnnenAktoer: Double? = null,
	val hvorMyeDekkesAvNAV: Double? = null,
	val skolenivaa: Skolenivaaer,
	val prosentandelForUtdanning: Int,
	val beloep: Int,
	val erUtgifterDekket: ErUtgifterDekket
)

@JacksonXmlRootElement(localName = "Maalgruppeinformasjon")
@JsonPropertyOrder("periode", "kilde", "maalgruppetype")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Maalgruppeinformasjon(
	@JacksonXmlProperty(localName = "periode")
	val periode: Periode,

	val kilde: String,

	@JacksonXmlText
	val maalgruppetype: Maalgruppetyper
)

@JacksonXmlRootElement(localName = "Maalgruppetyper")
@JsonPropertyOrder("value")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Maalgruppetyper(
	@XmlElement(required = true)
	@XmlValue
	@JacksonXmlText
	val value: String,  // Ref. enum Maalgrupper

	//@XmlSchemaType(name = "anyURI")
	@JacksonXmlProperty(isAttribute = true, localName = "kodeRef")
	protected var kodeRef: String? = null,

	//@XmlSchemaType(name = "anyURI")
	@JacksonXmlProperty(isAttribute = true, localName = "kodeverksRef")
	val kodeverksRef: String? = ""
)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Periode", propOrder = ["fom", "tom"])
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Periode(
	@XmlElement(required = true)
	@XmlSchemaType(name = "date")
	val fom: String,
	@XmlSchemaType(name = "date")
	val tom: String
)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
	name = "Reiseutgifter",
	propOrder = ["dagligReise", "reiseObligatoriskSamling", "reiseVedOppstartOgAvsluttetAktivitet", "reisestoenadForArbeidssoeker"]
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Reiseutgifter(
	val dagligReise: DagligReise? = null,
	val reiseObligatoriskSamling: ReiseObligatoriskSamling? = null,
	val reiseVedOppstartOgAvsluttetAktivitet: ReiseVedOppstartOgAvsluttetAktivitet? = null,
	val reisestoenadForArbeidssoeker: ReisestoenadForArbeidssoeker? = null
)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
	name = "Rettighetstype",
	propOrder = ["reiseutgifter", "flytteutgifter", "boutgifter", "laeremiddelutgifter", "tilsynsutgifter"]
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Rettighetstype(
	val reiseutgifter: Reiseutgifter? = null,
	val flytteutgifter: Flytteutgifter? = null,
	val boutgifter: Boutgifter? = null,
	val laeremiddelutgifter: Laeremiddelutgifter? = null,
	val tilsynsutgifter: Tilsynsutgifter? = null
)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DrosjeTransportutgifter", propOrder = ["beloep"])
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class DrosjeTransportutgifter(
	val beloep: Int
)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Formaal")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Formaal(
	@XmlValue
	@JacksonXmlText
	val value: String,  // Ref. enum FormaalKodeverk

	@XmlAttribute(name = "kodeRef")
	@XmlSchemaType(name = "anyURI")
	val kodeRef: String? = null,

	@XmlAttribute(name = "kodeverksRef")
	@XmlSchemaType(name = "anyURI")
	protected var kodeverksRef: String? = null

)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Innsendingsintervaller")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Innsendingsintervaller(
	@XmlValue
	@JacksonXmlText
	val value: String,  // Ref. enum InnsendingsintervallerKodeverk

	@XmlAttribute(name = "kodeRef")
	@XmlSchemaType(name = "anyURI")
	protected var kodeRef: String? = null,

	@XmlAttribute(name = "kodeverksRef")
	@XmlSchemaType(name = "anyURI")
	val kodeverksRef: String? = ""
)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "KollektivTransportutgifter", propOrder = ["beloepPerMaaned"])
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class KollektivTransportutgifter(
	val beloepPerMaaned: Int
)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
	name = "ReiseObligatoriskSamling",
	propOrder = ["periode", "reiseadresser", "avstand", "samlingsperiode", "alternativeTransportutgifter"]
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class ReiseObligatoriskSamling(
	@XmlElement(required = true)
	val periode: Periode,
	val reiseadresser: String,
	val avstand: Int,
	val samlingsperiode: List<Periode>,
	val alternativeTransportutgifter: AlternativeTransportutgifter
)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
	name = "ReiseOppstartOgAvsluttetAktivitet",
	propOrder = ["periode", "harSaerskilteBehov", "aktivitetsstedAdresse", "avstand", "antallReiser", "harBarnUnderFemteklasse", "harBarnUnderAtten", "alternativeTransportutgifter"]
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class ReiseVedOppstartOgAvsluttetAktivitet(
	@XmlElement(required = true)
	val periode: Periode,
	val aktivitetsstedAdresse: String,
	val avstand: Int,
	val antallReiser: Int,
	val harBarnUnderFemteklasse: Boolean,
	val harBarnUnderAtten: Boolean?,
	val alternativeTransportutgifter: AlternativeTransportutgifter
)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
	name = "ReisestoenadForArbeidssoeker",
	propOrder = ["reisedato", "harMottattDagpengerSisteSeksMaaneder", "formaal", "adresse", "avstand", "erUtgifterDekketAvAndre", "erVentetidForlenget", "finnesTidsbegrensetbortfall", "alternativeTransportutgifter"]
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class ReisestoenadForArbeidssoeker(
	@XmlElement(required = true)
	@XmlSchemaType(name = "date")
	val reisedato: String,
	val harMottattDagpengerSisteSeksMaaneder: Boolean,
	val formaal: Formaal,
	val adresse: String,
	val avstand: Int,

	val erUtgifterDekketAvAndre: Boolean,

	val erVentetidForlenget: Boolean,
	val finnesTidsbegrensetbortfall: Boolean,
	val alternativeTransportutgifter: AlternativeTransportutgifter
)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Skolenivaaer")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Skolenivaaer(
	@XmlValue
	@JacksonXmlText
	val value: String, // Ref. enum SkolenivaaerKodeverk

	@XmlAttribute(name = "kodeRef")
	@XmlSchemaType(name = "anyURI")
	protected var kodeRef: String? = null,

	@XmlAttribute(name = "kodeverksRef")
	@XmlSchemaType(name = "anyURI")
	val kodeverksRef: String? = ""
)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
	name = "Tilleggsstoenadsskjema",
	propOrder = ["aktivitetsinformasjon", "personidentifikator", "maalgruppeinformasjon", "rettighetstype"]
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Tilleggsstoenadsskjema(
	val aktivitetsinformasjon: Aktivitetsinformasjon,
	val personidentifikator: String,
	val maalgruppeinformasjon: Maalgruppeinformasjon,
	val rettighetstype: Rettighetstype
)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Tilsynskategorier")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Tilsynskategorier(
	@XmlValue
	@JacksonXmlText
	val value: String,  // Ref. enum TilsynForetasAvKodeverk

	@XmlAttribute(name = "kodeRef")
	@XmlSchemaType(name = "anyURI")
	protected var kodeRef: String? = null,

	@XmlAttribute(name = "kodeverksRef")
	@XmlSchemaType(name = "anyURI")
	val kodeverksRef: String? = ""
)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Tilsynsutgifter", propOrder = ["tilsynsutgifterBarn", "tilsynsutgifterFamilie"])
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Tilsynsutgifter(
	val tilsynsutgifterBarn: TilsynsutgifterBarn? = null,
	val tilynsutgifterFamilie: TilsynsutgifterFamilie? = null
)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "TilsynsutgifterBarn", propOrder = ["periode", "barn", "annenForsoergerperson"])
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class TilsynsutgifterBarn(
	val periode: Periode,
	val barn: List<Barn>,
	val annenForsoergerperson: String? = null
)

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
	name = "TilsynsutgifterFamilie",
	propOrder = ["periode", "deltTilsyn", "annenTilsynsperson", "tilsynForetasAv", "tilsynsmottaker", "maanedligUtgiftTilsynFam"]
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class TilsynsutgifterFamilie(
	val periode: Periode,
	val deltTilsyn: Boolean,
	val annenTilsynsperson: String? = null,
	val tilsynForetasAv: String? = null,
	val tilsynsmottaker: String,
	val maanedligUtgiftTilsynFam: Int
)
