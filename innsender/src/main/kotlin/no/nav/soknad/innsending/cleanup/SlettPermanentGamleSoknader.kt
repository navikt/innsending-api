package no.nav.soknad.innsending.cleanup

import no.nav.soknad.innsending.service.SoknadService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class SlettPermanentGamleSoknader(private val soknadService: SoknadService) {

	val logger: Logger = LoggerFactory.getLogger(javaClass)

	@Value("\${cron.slettPermanentEldreEnn}")
	private lateinit var dagerGamleString: String

	@Scheduled(cron = "\${cron.startSlettPermanentIkkeInnsendteSoknader}")
	fun fjernPermanentGamleIkkeInnsendteSoknader() {
		try {
			if (LeaderSelectionUtility().isLeader()) {
				soknadService.slettGamleSoknader(dagerGamleString.toLong(), true)
			}
		} catch (ex: Exception) {
			logger.warn("Fjerning av gamle ikke innsendte søknader feilet med ${ex.message}")
		}
	}


}