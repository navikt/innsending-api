package no.nav.soknad.innsending.util.validators

import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.util.models.hoveddokument
import no.nav.soknad.innsending.util.models.hoveddokumentVariant

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
		validerVedleggVedOppdatering(it, eksisterendeSoknad)
	}
}

// Ved oppdatering må noen felter fra det eksisterende vedlegget være like det som blir sendt inn
private fun validerVedleggVedOppdatering(vedlegg: VedleggDto, eksisterendeSoknad: DokumentSoknadDto) {

	val eksisterendeVedlegg = when {
		vedlegg.erHoveddokument && !vedlegg.erVariant -> eksisterendeSoknad.hoveddokument
		vedlegg.erHoveddokument && vedlegg.erVariant -> eksisterendeSoknad.hoveddokumentVariant
		else -> eksisterendeSoknad.vedleggsListe.find { it.formioId == vedlegg.formioId }
	}

	if (eksisterendeVedlegg == null && vedlegg.erHoveddokument) {
		throw BackendErrorException("Finner ikke hoveddokumentet som skal oppdateres")
	} else if (eksisterendeVedlegg == null) {
		return
	}

	val likeFelterVedOppdatering = listOf(
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
