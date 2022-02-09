package no.nav.soknad.innsending.consumerapis.soknadsfillager

import no.nav.soknad.innsending.dto.VedleggDto

interface FillagerInterface {

	fun lagreFiler(innsendingsId: String, vedleggDtos: List<VedleggDto>)

	fun hentFiler(innsendingsId: String, vedleggDtos: List<VedleggDto>): List<VedleggDto>

	fun slettFiler(innsendingsId: String, vedleggDtos: List<VedleggDto>)
}
