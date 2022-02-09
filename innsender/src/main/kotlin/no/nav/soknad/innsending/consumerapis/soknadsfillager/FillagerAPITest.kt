package no.nav.soknad.innsending.consumerapis.soknadsfillager

import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import no.nav.soknad.innsending.dto.VedleggDto
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("spring | test | docker | default")
@Qualifier("fillager")
class FillagerAPITest(private val restConfig: RestConfig): FillagerInterface, HealthRequestInterface {

	override fun ping(): String {
		return "pong"
	}
	override fun isReady(): String {
		return "ok"
	}
	override fun isAlive(): String {
		return "ok"
	}

	override fun lagreFiler(innsendingsId: String, vedleggDtos: List<VedleggDto>) {

	}

	override fun hentFiler(innsendingsId: String, vedleggDtos: List<VedleggDto>): List<VedleggDto> {
		return emptyList()
	}

	override fun slettFiler(innsendingsId: String, vedleggDtos: List<VedleggDto>) {

	}

}
