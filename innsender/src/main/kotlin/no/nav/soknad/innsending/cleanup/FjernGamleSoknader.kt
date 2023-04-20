package no.nav.soknad.innsending.cleanup

import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.util.Constants
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class FjernGamleSoknader(
	private val soknadService: SoknadService,
	private val leaderSelectionUtility: LeaderSelectionUtility
) {

	val logger: Logger = LoggerFactory.getLogger(javaClass)

	@Scheduled(cron = "\${cron.startSlettGamleIkkeInnsendteSoknader}")
	fun fjernGamleIkkeInnsendteSoknader() {
		try {
			if (leaderSelectionUtility.isLeader()) {
				soknadService.slettGamleSoknader(Constants.DEFAULT_LEVETID_OPPRETTET_SOKNAD)
			}
		} catch (ex: Exception) {
			logger.warn("Fjerning av gamle ikke innsendte søknader feilet med ${ex.message}")
		}
	}

}
