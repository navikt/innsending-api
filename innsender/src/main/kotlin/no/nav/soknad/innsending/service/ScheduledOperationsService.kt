package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ScheduledOperationsService(
	private val soknadRepository: SoknadRepository,
	private val innsenderMetrics: InnsenderMetrics
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun checkIfApplicationsAreArchived(offsetMinutes: Long) {
		val soknaderAbsentInArchive =
			soknadRepository.countInnsendtIkkeBehandlet(LocalDateTime.now().minusMinutes(offsetMinutes))
		if (soknaderAbsentInArchive > 0) {
			logger.warn("Total number of applications not yet processed for archiving by soknadsarkiverer: $soknaderAbsentInArchive")
			val notProcessedForArchiving =
				soknadRepository.findInnsendtAndArkiveringsStatusIkkeSatt(LocalDateTime.now().minusMinutes(offsetMinutes))
			logger.info("Applications not yet picked up and processed by soknadsarkiverer: $notProcessedForArchiving")
		} else {
			logger.info("Total number of applications absent in archive: $soknaderAbsentInArchive")
		}

		innsenderMetrics.setAbsentInArchive(soknaderAbsentInArchive)
		innsenderMetrics.setArchivingFailed(soknadRepository.countArkiveringFeilet())

	}

}
