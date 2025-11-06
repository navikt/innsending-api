package no.nav.soknad.innsending.consumerapis.soknadsmottaker

import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import no.nav.soknad.innsending.model.AvsenderDto
import no.nav.soknad.innsending.model.BrukerDto
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.util.mapping.translate
import no.nav.soknad.innsending.util.maskerFnr
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

	override fun sendInnSoknad(soknadDto: DokumentSoknadDto, vedleggsListe: List<VedleggDto>, avsenderDto: AvsenderDto, brukerDto: BrukerDto?) {
		if (soknadDto.visningsType == VisningsType.nologin) {
			val innsending = translate(soknadDto, vedleggsListe, avsenderDto, brukerDto)
			logger.info("${soknadDto.innsendingsId}: klar til å sende inn (nologin)\n${maskerFnr(innsending)}")
		} else {
			val personId = soknadDto.brukerId ?: throw IllegalStateException("Kan ikke sende inn søknad uten brukerId")
			val soknad = translate(soknadDto, vedleggsListe, personId)
			logger.info("${soknadDto.innsendingsId}: klar til å sende inn\n${maskerFnr(soknad)}")
		}
	}

}
