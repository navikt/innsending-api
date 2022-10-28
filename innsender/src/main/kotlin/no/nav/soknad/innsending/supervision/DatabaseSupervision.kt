package no.nav.soknad.innsending.supervision

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

	@Scheduled(cron = everyFiveMinutes)
	fun databaseSupervisionStart() {
		try {
			collectDbStat()
		} catch (e: Exception) {
			logger.error("Something went wrong when performing database supervision", e)
		}
	}

	private fun collectDbStat() {
		val count = filRepository.count()
		logger.info("Number of rows in the database: $count")

		val databaseSize = filRepository.totalDbSize()
		logger.info("Total database size: $databaseSize")

		innsenderMetrics.databaseSizeSet(databaseSize)
	}
}

private const val everyFiveMinutes = "0 */5 * * * *"
