package no.nav.soknad.innsending.supervision

import no.nav.soknad.innsending.cleanup.LeaderSelectionUtility
import no.nav.soknad.innsending.service.ScheduledOperationsService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@EnableScheduling
@Component
class VerifyArchivedApplications(
	private val leaderSelectionUtility: LeaderSelectionUtility,
	private val scheduledOperationsService: ScheduledOperationsService,
	private val metrics: InnsenderMetrics,
	@Value("\${verifyArchivedApplications.offsetHours}") private val offsetHours: Long,
	@Value("\${verifyArchivedApplications.timespanHours}") private val timespanHours: Long,
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	private val runSupervision = false

	init {
		logger.info("Initializing scheduled job ${javaClass.kotlin.simpleName} (offsetHours=$offsetHours, timespanHours=${timespanHours})")
	}

	@Scheduled(cron = "\${cron.runVerifyArchivedApplications}")
	fun run() {
		try {
			if (leaderSelectionUtility.isLeader() && runSupervision) {
				scheduledOperationsService.updateSoknadErArkivert(timespanHours, offsetHours)
				metrics.updateJobLastSuccess(JOB_NAME)
			}
		} catch (e: Exception) {
			logger.error("Something went wrong running scheduled job ${javaClass.kotlin.simpleName}", e)
		}
	}

}

private const val JOB_NAME = "SoknadArkiveringskontroll"
