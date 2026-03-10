package no.nav.soknad.innsending.supervision

import no.nav.soknad.innsending.cleanup.LeaderSelection
import no.nav.soknad.innsending.service.InnsendingService
import no.nav.soknad.innsending.service.ScheduledOperationsService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@EnableScheduling
@Component
class SendInSupervision(
	private val leaderSelection: LeaderSelection,
	private val scheduledOperationsService: ScheduledOperationsService,
	@Value("\${checkNotSentInApplications.offsetMinutes}") private val offsetMinutes: Long,
	private val innsendingService: InnsendingService
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	@Scheduled(cron = every5Minute_Start0MinutePassedHour)
	fun runSendInSupervision() {
		try {
			logger.info("Running scheduled job ${javaClass.kotlin.simpleName}")
			if (leaderSelection.isLeader()) {
				val notSentInApplications = scheduledOperationsService.findNotSentInApplications(offsetMinutes)
				notSentInApplications.forEach {innsendingService.sendInnForArkivering(it)}
			}
		} catch (e: Exception) {
			logger.error("Something went wrong running scheduled job ${javaClass.kotlin.simpleName}", e)
		}
 	}

}

private const val every5Minute_Start0MinutePassedHour = "0 0/5 * * * *"

