package no.nav.soknad.innsending.consumerapis.soknadsmottaker

import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.VedleggDto
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("spring | test | docker | default")
@Qualifier("mottaker")
class MottakerAPITest(private val restConfig: RestConfig): MottakerInterface, HealthRequestInterface {

	override fun ping(): String {
		return "pong"
	}
	override fun isReady(): String {
		return "ok"
	}
	override fun isAlive(): String {
		return "ok"
	}

	override fun sendInnSoknad(soknadDto: DokumentSoknadDto, vedleggDtos: List<VedleggDto>){

	}

}
