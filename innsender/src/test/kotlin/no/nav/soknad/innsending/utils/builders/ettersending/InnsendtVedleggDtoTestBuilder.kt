package no.nav.soknad.innsending.utils.builders.ettersending

import no.nav.soknad.innsending.model.InnsendtVedleggDto
import no.nav.soknad.innsending.utils.Skjema

class InnsendtVedleggDtoTestBuilder {
	private var vedleggsnr: String = Skjema.generateVedleggsnr()
	private var tittel: String? = "Vedleggstittel"

	fun vedleggsnr(vedleggsnr: String) = apply { this.vedleggsnr = vedleggsnr }
	fun tittel(tittel: String?) = apply { this.tittel = tittel }

	fun build(): InnsendtVedleggDto {
		return InnsendtVedleggDto(
			vedleggsnr = vedleggsnr,
			tittel = tittel
		)
	}
}
