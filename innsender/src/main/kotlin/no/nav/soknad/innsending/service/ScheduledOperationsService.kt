package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.repository.SoknadDbData
import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ScheduledOperationsService(
	private val soknadRepository: SoknadRepository,
	private val safService: SafService,
	private val innsenderMetrics: InnsenderMetrics
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun updateSoknadErArkivert(timespanHours: Long, offsetHours: Long) {
		val now = LocalDateTime.now()
		val start = now.minusHours(timespanHours + offsetHours)
		val end = now.minusHours(offsetHours)
		logger.info("Verifying that applications submitted between [$start -> $end] exist in Joark...")

		val emptyPair = Pair(emptyList<SoknadDbData>(), emptyList<SoknadDbData>())
		val (existsInJoark, absentInJoark) = soknadRepository
			.findAllNotArchivedAndInnsendtdatoBetween(start, end)
			.groupBy { soknad -> soknad.brukerid }
			.map { entry -> existsInJoark(entry.key, entry.value) }
			.fold(emptyPair) { acc, pair -> Pair(acc.first + pair.first, acc.second + pair.second) }

		if (existsInJoark.isNotEmpty()) {
			logger.debug("Antall arkivert ${existsInJoark.size}")
			soknadRepository.updateErArkivert(true, existsInJoark.map { soknad -> soknad.innsendingsid })
		}

		if (absentInJoark.isNotEmpty()) {
			val innsendingsIdList = absentInJoark.map { soknad -> soknad.innsendingsid }
			soknadRepository.updateErArkivert(false, innsendingsIdList)
			logger.error("Detected ${absentInJoark.size} submitted application(s) [$start -> $end] which do not exist in Joark: $innsendingsIdList")
		}

		logger.info("Done verifying applications submitted between [$start -> $end]")

		val soknaderAbsentInArchive = soknadRepository.countErarkivertIs(false)
		logger.info("Total number of applications absent in archive: $soknaderAbsentInArchive")
		innsenderMetrics.absentInArchive(soknaderAbsentInArchive)
	}

	private fun existsInJoark(brukerid: String, soknader: List<SoknadDbData>): Pair<List<SoknadDbData>, List<SoknadDbData>> {
		val arkiverteSoknader = safService.hentArkiverteSaker(brukerid)
		return soknader.partition { soknad -> arkiverteSoknader.any { sak -> sak.eksternReferanseId == soknad.innsendingsid } }
	}

}
