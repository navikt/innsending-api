package no.nav.soknad.innsending.utils.builders.ettersending

import no.nav.soknad.innsending.model.InnsendtVedleggDto

class InnsendtVedleggDtoTestBuilder {
	private var vedleggsnr: String = "${('A'..'Z').random()}${(1..9).random()}"
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
