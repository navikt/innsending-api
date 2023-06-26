package no.nav.soknad.innsending.consumerapis.soknadsmottaker

import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.service.maskerFnr
import no.nav.soknad.innsending.service.translate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("local | test | docker")
@Qualifier("mottaker")
class MottakerAPITest : MottakerInterface, HealthRequestInterface {

	private val logger = LoggerFactory.getLogger(javaClass)

	override fun ping(): String {
		return "pong"
	}

	override fun isReady(): String {
		return "ok"
	}

	override fun isAlive(): String {
		return "ok"
	}

	override fun sendInnSoknad(soknadDto: DokumentSoknadDto, vedleggsListe: List<VedleggDto>) {
		val soknad = translate(soknadDto, vedleggsListe)
		logger.info("${soknadDto.innsendingsId}: klar til å sende inn\n${maskerFnr(soknad)}")
	}

}
