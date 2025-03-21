package no.nav.soknad.innsending.supervision

import no.nav.soknad.innsending.cleanup.LeaderSelectionUtility
import no.nav.soknad.innsending.repository.FilRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@EnableScheduling
@Component
class DatabaseSupervision(
	private val filRepository: FilRepository,
	private val innsenderMetrics: InnsenderMetrics
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	@Scheduled(cron = everyHour)
	fun databaseSupervisionStart() {
		try {
			if (LeaderSelectionUtility().isLeader()) {
				collectDbStat()
			}
		} catch (e: Exception) {
			logger.error("Something went wrong when performing database supervision", e)
		}
	}

	private fun collectDbStat() {
		val databaseSize = filRepository.totalDbSize()
		logger.info("Total database size: $databaseSize")

		innsenderMetrics.setDatabaseSize(databaseSize)
	}
}

private const val everyHour = "0 0 * * * *"
