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

	fun checkIfApplicationsAreArchived(timespanHours: Long, offsetHours: Long) {
		val now = LocalDateTime.now()
		val start = now.minusHours(timespanHours + offsetHours)
		val end = now.minusHours(offsetHours)
		logger.info("Verifying that applications submitted between [$start -> $end] exist in Joark...")

		val absentInJoark = soknadRepository
			.findAllNotArchivedAndInnsendtdatoBetween(start, end)

		if (absentInJoark.isNotEmpty()) {
			val innsendingsIdList = absentInJoark.map { soknad -> soknad.innsendingsid }
			logger.error("Detected ${absentInJoark.size} submitted application(s) [$start -> $end] which do not exist in Joark: $innsendingsIdList")
		}

		val soknaderAbsentInArchive = soknadRepository.countIkkeArkivert()
		logger.info("Total number of applications absent in archive: $soknaderAbsentInArchive")
		innsenderMetrics.absentInArchive(soknaderAbsentInArchive)
	}

}
