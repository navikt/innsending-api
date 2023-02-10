package no.nav.soknad.innsending.supervision

import no.nav.soknad.innsending.cleanup.LeaderSelectionUtility
import no.nav.soknad.innsending.repository.SoknadRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

class SoknadSupervision(
	private val soknadRepository: SoknadRepository,
	private val innsenderMetrics: InnsenderMetrics
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	@Scheduled(cron = everyFifteenMinutes)
	fun run() {
		try {
			if (LeaderSelectionUtility().isLeader()) {
				collectSoknadStat()
			}
		} catch (e: Exception) {
			logger.error("Something went wrong when performing soknad supervision", e)
		}
	}

	private fun collectSoknadStat() {
		val soknaderAbsentInArchive = soknadRepository.countErarkivertIs(false)
		logger.info("Number of applications absent in joark: $soknaderAbsentInArchive")
		innsenderMetrics.absentInArchive(soknaderAbsentInArchive)
	}

}

private const val everyFifteenMinutes = "0 */15 * * * *"
