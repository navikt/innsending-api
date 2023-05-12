package no.nav.soknad.innsending.util.validators

import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.VedleggDto

// Ved oppdatering må noen felter fra den eksisterende søknaden være like den som blir sendt inn
fun DokumentSoknadDto.validerSoknadVedOppdatering(eksisterendeSoknad: DokumentSoknadDto) {
	val likeFelterVedOppdatering =
		listOf(DokumentSoknadDto::brukerId, DokumentSoknadDto::skjemanr)

	validerLikeFelter(
		this,
		eksisterendeSoknad,
		likeFelterVedOppdatering
	)
}

fun DokumentSoknadDto.validerVedleggsListeVedOppdatering(eksisterendeSoknad: DokumentSoknadDto) {
	this.vedleggsListe.forEach {
		validerVedleggVedOppdatering(it, eksisterendeSoknad.vedleggsListe)
	}
}

// Ved oppdatering må noen felter fra det eksisterende vedlegget være like det som blir sendt inn
private fun validerVedleggVedOppdatering(vedlegg: VedleggDto, eksisterendeVedleggsListe: List<VedleggDto>) {
	val eksisterendeVedlegg =
		eksisterendeVedleggsListe.find { it.vedleggsnr == vedlegg.vedleggsnr && it.mimetype == vedlegg.mimetype } ?: return

	val likeFelterVedOppdatering =
		listOf(
			VedleggDto::erHoveddokument,
			VedleggDto::erPakrevd,
			VedleggDto::erVariant,
			VedleggDto::vedleggsnr,
			VedleggDto::formioId
		)

	validerLikeFelter<VedleggDto>(
		vedlegg,
		eksisterendeVedlegg,
		likeFelterVedOppdatering
	)
}
