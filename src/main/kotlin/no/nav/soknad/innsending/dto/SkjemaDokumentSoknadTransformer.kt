package no.nav.soknad.innsending.dto

import no.nav.soknad.innsending.repository.OpplastingsStatus
import no.nav.soknad.innsending.repository.SoknadsStatus
import java.time.LocalDateTime

class SkjemaDokumentSoknadTransformer(private val input: SkjemaDto) {

	fun apply(): DokumentSoknadDto = input.toDokumentSoknadDto()

	private fun SkjemaDto.toDokumentSoknadDto() = DokumentSoknadDto(null, null, null, brukerId,
		skjemanr, tittel, tema, spraak, SoknadsStatus.Opprettet, LocalDateTime.now(), LocalDateTime.now(), null, konverterTilvedleggsListe(vedleggsListe) )

	private fun konverterTilvedleggsListe(List: List<SkjemaDokumentDto>) = List.map { it.toVedleggDto() }

	private fun SkjemaDokumentDto.toVedleggDto() = VedleggDto(null, vedleggsnr, tittel, null, mimetype, document,
		erHoveddokument, erVariant, erPdfa ?: false, null,toOpplastingsstatus(opplastingsStatus) ?: OpplastingsStatus.IKKE_VALGT, LocalDateTime.now() )

	private fun toOpplastingsstatus(status: OpplastingsStatusDto) = OpplastingsStatus.values().find { it.name == status.name }


}
