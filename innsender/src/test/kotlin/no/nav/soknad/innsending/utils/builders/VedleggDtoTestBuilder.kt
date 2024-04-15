package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.model.Mimetype
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.Skjema.generateVedleggsnr
import java.time.OffsetDateTime
import java.util.*

data class VedleggDtoTestBuilder(
	var tittel: String = "Forsikring mot ansvar for sykepenger i arbeidsgiverperioden for små bedrifter.",
	var label: String = "Inntektsopplysninger for selvstendig næringsdrivende og frilansere som skal ha foreldrepenger eller svangerskapspenger.",
	var erHoveddokument: Boolean = false,
	var erVariant: Boolean = false,
	var erPdfa: Boolean = true,
	var erPakrevd: Boolean = true,
	var opplastingsStatus: OpplastingsStatusDto = OpplastingsStatusDto.IkkeValgt,
	var opprettetdato: OffsetDateTime = OffsetDateTime.now(),
	var id: Long? = null,
	var vedleggsnr: String? = generateVedleggsnr(),
	var beskrivelse: String? = "Dette er opplysninger som er nødvendig for beregning av utbetaling av foreldrepenger eller svangerskapspenger.",
	var uuid: String? = UUID.randomUUID().toString(),
	var mimetype: Mimetype? = null,
	var document: ByteArray? = null,
	var skjemaurl: String? = "https://cdn.sanity.io/files/gx9wf39f/soknadsveiviser-p/c95447be624fb0ea7c27e1ff7518604bb7aacd71.pdf",
	var innsendtdato: OffsetDateTime? = OffsetDateTime.now(),
	var formioId: String? = UUID.randomUUID().toString()
) {

	fun asHovedDokument(): VedleggDtoTestBuilder {
		erHoveddokument = true
		erVariant = false
		document = Hjelpemetoder.getBytesFromFile("/litenPdf.pdf")
		mimetype = Mimetype.applicationSlashPdf
		formioId = null
		opplastingsStatus = OpplastingsStatusDto.LastetOpp
		return this
	}

	fun asHovedDokumentVariant(): VedleggDtoTestBuilder {
		erHoveddokument = true
		erVariant = true
		document = Hjelpemetoder.getBytesFromFile("/__files/sanity.json")
		mimetype = Mimetype.applicationSlashJson
		formioId = null
		opplastingsStatus = OpplastingsStatusDto.LastetOpp
		return this
	}

	fun build(): VedleggDto {
		return VedleggDto(
			tittel,
			label,
			erHoveddokument,
			erVariant,
			erPdfa,
			erPakrevd,
			opplastingsStatus,
			opprettetdato,
			id,
			vedleggsnr,
			beskrivelse,
			uuid,
			mimetype,
			document,
			skjemaurl,
			innsendtdato,
			formioId
		)
	}
}

