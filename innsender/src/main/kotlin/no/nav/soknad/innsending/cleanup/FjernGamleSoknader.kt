package no.nav.soknad.innsending.cleanup

import no.nav.soknad.innsending.service.NotificationService
import no.nav.soknad.innsending.service.SoknadService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class FjernGamleSoknader(
	private val soknadService: SoknadService,
	private val notificationService: NotificationService,
	private val leaderSelectionUtility: LeaderSelection
) {

	val logger: Logger = LoggerFactory.getLogger(javaClass)

	@Scheduled(cron = "\${cron.startSlettGamleIkkeInnsendteSoknader}")
	fun fjernGamleIkkeInnsendteSoknader() {
		try {
			if (leaderSelectionUtility.isLeader()) {
				val innsendingsIdListe = soknadService.deleteSoknadBeforeCutoffDate(OffsetDateTime.now())
				innsendingsIdListe.forEach {
					notificationService.close(it)
				}
			}
		} catch (ex: Exception) {
			logger.warn("Fjerning av gamle ikke innsendte søknader feilet med ${ex.message}")
		}
	}

}
