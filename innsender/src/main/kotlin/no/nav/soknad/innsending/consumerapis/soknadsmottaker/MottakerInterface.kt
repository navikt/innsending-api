package no.nav.soknad.innsending.consumerapis.soknadsmottaker

import no.nav.soknad.innsending.model.AvsenderDto
import no.nav.soknad.innsending.model.BrukerDto
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.VedleggDto
import org.springframework.resilience.annotation.Retryable

interface MottakerInterface {

	@Retryable(
		maxRetries = 3,
		delay = 500,
		multiplier = 2.0,
		jitter = 2
	)
	fun sendInnSoknad(soknadDto: DokumentSoknadDto, vedleggsListe: List<VedleggDto>, avsenderDto: AvsenderDto, brukerDto: BrukerDto? = null)
}
