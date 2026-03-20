package no.nav.soknad.innsending.consumerapis.soknadsmottaker

import no.nav.soknad.innsending.model.AvsenderDto
import no.nav.soknad.innsending.model.BrukerDto
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.VedleggDto
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable

interface MottakerInterface {

	@Retryable(
		maxAttempts = 3,
		backoff = Backoff(delay = 500)
	)
	fun sendInnSoknad(soknadDto: DokumentSoknadDto, vedleggsListe: List<VedleggDto>, avsenderDto: AvsenderDto, brukerDto: BrukerDto? = null)
}
