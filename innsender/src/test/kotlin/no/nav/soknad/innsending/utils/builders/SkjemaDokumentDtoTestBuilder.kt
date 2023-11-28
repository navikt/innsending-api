package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.model.Mimetype
import no.nav.soknad.innsending.model.SkjemaDokumentDto
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.Skjema.generateVedleggsnr
import java.util.*

data class SkjemaDokumentDtoTestBuilder(
	var vedleggsnr: String = generateVedleggsnr(),
	var tittel: String = "Forsikring mot ansvar for sykepenger i arbeidsgiverperioden for små bedrifter.",
	var label: String = "Inntektsopplysninger for selvstendig næringsdrivende og frilansere som skal ha foreldrepenger eller svangerskapspenger.",
	var pakrevd: Boolean = true,
	var beskrivelse: String = "Dette er opplysninger som er nødvendig for beregning av utbetaling av foreldrepenger eller svangerskapspenger.",
	var mimetype: Mimetype? = null,
	var document: ByteArray? = null,
	var propertyNavn: String? = null,
	var formioId: String? = UUID.randomUUID().toString()
) {

	fun asHovedDokument(skjemanr: String): SkjemaDokumentDtoTestBuilder {
		document = Hjelpemetoder.getBytesFromFile("/litenPdf.pdf")
		mimetype = Mimetype.applicationSlashPdf
		formioId = null
		vedleggsnr = skjemanr
		return this
	}

	fun asHovedDokumentVariant(skjemanr: String): SkjemaDokumentDtoTestBuilder {
		document = Hjelpemetoder.getBytesFromFile("/__files/sanity.json")
		mimetype = Mimetype.applicationSlashJson
		formioId = null
		vedleggsnr = skjemanr
		return this
	}

	fun build(): SkjemaDokumentDto {
		return SkjemaDokumentDto(
			vedleggsnr = vedleggsnr,
			tittel = tittel,
			label = label,
			pakrevd = pakrevd,
			beskrivelse = beskrivelse,
			mimetype = mimetype,
			document = document,
			propertyNavn = propertyNavn,
			formioId = formioId
		)
	}
}
