package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.model.Mimetype
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.SkjemaDokumentDtoV2
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.Skjema.generateVedleggsnr
import java.util.UUID

class SkjemaDokumentDtoV2TestBuilder(
var vedleggsnr: String = generateVedleggsnr(),
var tittel: String = "Forsikring mot ansvar for sykepenger i arbeidsgiverperioden for små bedrifter.",
var label: String = "Inntektsopplysninger for selvstendig næringsdrivende og frilansere som skal ha foreldrepenger eller svangerskapspenger.",
var pakrevd: Boolean = true,
var beskrivelse: String = "Dette er opplysninger som er nødvendig for beregning av utbetaling av foreldrepenger eller svangerskapspenger.",
var mimetype: Mimetype? = null,
var document: ByteArray? = null,
var propertyNavn: String? = null,
var formioId: String = UUID.randomUUID().toString(),
var opplastingsStatus: OpplastingsStatusDto = OpplastingsStatusDto.IkkeValgt,
var filIdListe: List<String>? = null

) {

	var opplastingsValgKommentarLedetekst: String? = null
	var opplastingsValgKommentar: String? = null

	// Hoveddokument uses skjemanr as vedleggsnr
	fun asHovedDokument(skjemanr: String, withFile: Boolean = true): SkjemaDokumentDtoV2TestBuilder {
		if (withFile) {
			document = Hjelpemetoder.getBytesFromFile("/litenPdf.pdf")
			mimetype = Mimetype.applicationSlashPdf
			opplastingsStatus = OpplastingsStatusDto.LastetOpp
		}
		formioId = "1"
		vedleggsnr = skjemanr
		return this
	}

	// Hoveddokument uses skjemanr as vedleggsnr
	fun asHovedDokumentVariant(skjemanr: String, withFile: Boolean = true): SkjemaDokumentDtoV2TestBuilder {
		if (withFile) {
			document = Hjelpemetoder.getBytesFromFile("/__files/sanity.json")
			mimetype = Mimetype.applicationSlashJson
			opplastingsStatus = OpplastingsStatusDto.LastetOpp
		}
		formioId = "2"
		vedleggsnr = skjemanr
		return this
	}
	fun withOpplastingsValgKommentar(kommentar: String?) = apply { this.opplastingsValgKommentar = kommentar}
	fun withOpplastingsValgKommentarLedetekst(ledetekst: String?) = apply { this.opplastingsValgKommentarLedetekst = ledetekst}

	fun build(): SkjemaDokumentDtoV2 {
		return SkjemaDokumentDtoV2(
			vedleggsnr = vedleggsnr,
			tittel = tittel,
			label = label,
			pakrevd = pakrevd,
			beskrivelse = beskrivelse,
			mimetype = mimetype,
			document = document,
			propertyNavn = propertyNavn,
			fyllutId = formioId,
			opplastingsStatus = opplastingsStatus,
			filIdListe = filIdListe,
			opplastingsValgKommentarLedetekst = opplastingsValgKommentarLedetekst,
			opplastingsValgKommentar = opplastingsValgKommentar,
		)
	}
}
