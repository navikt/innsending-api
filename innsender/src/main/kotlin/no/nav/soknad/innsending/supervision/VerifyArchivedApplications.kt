package no.nav.soknad.innsending.supervision

import no.nav.soknad.innsending.cleanup.LeaderSelectionUtility
import no.nav.soknad.innsending.service.ScheduledOperationsService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@EnableScheduling
@Component
class VerifyArchivedApplications(
	private val scheduledOperationsService: ScheduledOperationsService,
) {

	private val logger = LoggerFactory.getLogger(javaClass)
	private val offsetHours: Long = 2 // hours ago
	private val timespanHours: Long = 24

	@Scheduled(cron = everyHour)
	fun run() {
		try {
			if (LeaderSelectionUtility().isLeader()) {
				scheduledOperationsService.updateSoknadErArkivert(timespanHours, offsetHours)
			}
		} catch (e: Exception) {
			logger.error("Something went wrong running scheduled ${javaClass.name}", e)
		}
	}

}

private const val everyHour = "0 * * * * *"
