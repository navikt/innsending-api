package no.nav.soknad.innsending.consumerapis.soknadsfillager

import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import no.nav.soknad.innsending.model.VedleggDto
import org.jboss.logging.Logger
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("default | test | spring | docker")
@Qualifier("fillager")
class FillagerAPITest(private val restConfig: RestConfig): FillagerInterface, HealthRequestInterface {

	val logger = Logger.getLogger(javaClass)

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
		logger.info("For $innsendingsId simulere sending av ${vedleggDtos.size} filer til soknadsfillager")
	}

	override fun hentFiler(innsendingsId: String, vedleggDtos: List<VedleggDto>): List<VedleggDto> {
		logger.info("For $innsendingsId simulere henting av ${vedleggDtos.size} filer fra soknadsfillager")
		return emptyList()
	}

	override fun slettFiler(innsendingsId: String, vedleggDtos: List<VedleggDto>) {
		logger.info("For $innsendingsId simulere sletting av ${vedleggDtos.size} filer fra soknadsfillager")
	}

}
