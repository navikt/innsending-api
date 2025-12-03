package no.nav.soknad.innsending.supervision

import no.nav.soknad.innsending.cleanup.LeaderSelection
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.service.config.ConfigDefinition
import no.nav.soknad.innsending.service.config.ConfigService
import no.nav.soknad.innsending.service.config.utils.dto.getLongValue
import no.nav.soknad.innsending.service.config.utils.dto.isEqualTo
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@EnableScheduling
@Component
class NologinSupervision(
	private val soknadRepository: SoknadRepository,
	private val configService: ConfigService,
	private val metrics: InnsenderMetrics,
	private val leaderSelection: LeaderSelection,
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	@Scheduled(cron = everyMinute)
	fun supervise() {
		try {
			if (leaderSelection.isLeader()) {
				val nologinMainSwitch = configService.getConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH)

				if (nologinMainSwitch.isEqualTo("on")) {
					val minutes = configService.getConfig(ConfigDefinition.NOLOGIN_MAX_SUBMISSIONS_WINDOW_MINUTES).getLongValue()
					val maxSubmissions = configService.getConfig(ConfigDefinition.NOLOGIN_MAX_SUBMISSIONS_COUNT).getLongValue()

					val since = LocalDateTime.now().minusMinutes(minutes)
					val count = soknadRepository.countRecentlySubmitted(VisningsType.nologin, since)
					logger.debug("Number of nologin submissions in the last $minutes minutes: $count (max is $maxSubmissions)")
					if (count > maxSubmissions) {
						logger.error("Disabling nologin due to high number of recent submissions ($count in the last $minutes minutes, max is $maxSubmissions).")
						configService.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "off", "system")
						metrics.setNologinMainSwitch(0)
					} else {
						logger.info("Nologin supervision completed successfully")
						metrics.setNologinMainSwitch(1)
					}

				} else {
					logger.debug("Nologin is turned off")
					metrics.setNologinMainSwitch(0)
				}
			}
		} catch (e: Exception) {
			logger.error("Something went wrong when performing nologin supervision", e)
		}
	}
}

private const val everyMinute = "0 * * * * *"
