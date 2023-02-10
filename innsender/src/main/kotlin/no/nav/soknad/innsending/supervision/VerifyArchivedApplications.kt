package no.nav.soknad.innsending.supervision

import no.nav.soknad.innsending.cleanup.LeaderSelectionUtility
import no.nav.soknad.innsending.repository.SoknadDbData
import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.service.SafService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@EnableScheduling
@Component
class VerifyArchivedApplications(
	private val soknadRepository: SoknadRepository,
	private val safService: SafService,
) {

	private val logger = LoggerFactory.getLogger(javaClass)
	private val offset: Long = 2 // hours ago

	@Scheduled(cron = everyHour)
	fun run() {
		try {
			if (LeaderSelectionUtility().isLeader()) {
				val now = LocalDateTime.now()
				val start = now.minusHours(24 + offset)
				val end = now.minusHours(offset)
				logger.info("Verifying that applications submitted between $start and $end exist in Joark...")

				val (existsInJoark, absentInJoark) = soknadRepository
					.findAllInnsendtdatoBetween(start, end)
					.filter { soknad -> soknad.erarkivert != true }
					.groupBy { soknad -> soknad.brukerid }
					.map { entry -> existsInJoark(entry.key, entry.value) }
					.reduce { acc, pair -> Pair(acc.first + pair.first, acc.second + pair.second) }

				if (existsInJoark.isNotEmpty()) {
					soknadRepository.updateErArkivert(true, existsInJoark.map { soknad -> soknad.innsendingsid })
				}

				if (absentInJoark.isNotEmpty()) {
					val innsendingsIdList = absentInJoark.map { soknad -> soknad.innsendingsid }
					soknadRepository.updateErArkivert(false, innsendingsIdList)
					logger.error("Detected ${absentInJoark.size} submitted application(s) [$start -> $end] which do not exist in Joark: $innsendingsIdList")
				}

				logger.info("Done verifying applications submitted between $start and $end")
			}
		} catch (e: Exception) {
			logger.error("Something went wrong when trying to lookup submitted applications in Joark", e)
		}
	}

	private fun existsInJoark(brukerid: String, soknader: List<SoknadDbData>): Pair<List<SoknadDbData>, List<SoknadDbData>> {
		val arkiverteSoknader = safService.hentInnsendteSoknader(brukerid)
		return soknader.partition { soknad -> arkiverteSoknader.any { sak -> sak.innsendingsId == soknad.innsendingsid } }
	}

}

private const val everyHour = "0 * * * * *"
