package no.nav.soknad.innsending.util.models

import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.util.Constants.DEFAULT_LEVETID_OPPRETTET_SOKNAD
import java.time.OffsetDateTime

val DokumentSoknadDto.hoveddokument: VedleggDto?
	get() = vedleggsListe.find {
		it.erHoveddokument && !it.erVariant
	}

val DokumentSoknadDto.hoveddokumentVariant: VedleggDto?
	get() = vedleggsListe.find {
		it.erHoveddokument && it.erVariant
	}

val DokumentSoknadDto.vedleggsListeUtenHoveddokument: List<VedleggDto>
	get() = vedleggsListe.filter {
		!it.erHoveddokument
	}

val DokumentSoknadDto.kanGjoreEndringer: Boolean
	get() = status == SoknadsStatusDto.Opprettet || status == SoknadsStatusDto.Utfylt

val DokumentSoknadDto.erEttersending: Boolean
	get() = ettersendingsId != null || visningsType == VisningsType.ettersending

val DokumentSoknadDto.sletteDato: OffsetDateTime?
	get() = skalSlettesDato ?: opprettetDato.plusDays(mellomlagringDager?.toLong() ?: DEFAULT_LEVETID_OPPRETTET_SOKNAD)

val SkjemaDto.sletteDato: OffsetDateTime?
	get() = skalSlettesDato ?: OffsetDateTime.now()
		.plusDays(mellomlagringDager?.toLong() ?: DEFAULT_LEVETID_OPPRETTET_SOKNAD)
