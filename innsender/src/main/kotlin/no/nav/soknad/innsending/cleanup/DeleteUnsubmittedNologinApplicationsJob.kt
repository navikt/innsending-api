package no.nav.soknad.innsending.cleanup

import no.nav.soknad.innsending.service.RepositoryUtils
import no.nav.soknad.innsending.service.fillager.FileStorage
import no.nav.soknad.innsending.service.fillager.FileStorageNamespace
import no.nav.soknad.innsending.util.Timer
import no.nav.soknad.innsending.util.mapping.toOffsetDateTime
import no.nav.soknad.innsending.util.stringextensions.toUUID
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class DeleteUnsubmittedNologinApplicationsJob(
	private val leaderSelectionUtility: LeaderSelection,
	private val fileStorage: FileStorage,
	private val repo: RepositoryUtils,
	@param:Value("\${cron.nologinDeleteUnsubmittedOlderThanDays}") private val nologinDeleteUnsubmittedOlderThanDays: Int
) {
	val logger: Logger = LoggerFactory.getLogger(javaClass)

	@Scheduled(cron = "\${cron.nologinDeleteUnsubmittedCron}")
	fun run() = runWithOlderThanDays(nologinDeleteUnsubmittedOlderThanDays)

	fun runWithOlderThanDays(days: Int) {
		try {
			if (leaderSelectionUtility.isLeader()) {
				val jobTimer = Timer.start()
				logger.info("${javaClass.simpleName} started")

				val cutoff = LocalDate.now().atStartOfDay().minusDays(days.toLong()).toOffsetDateTime()
				val applications = fileStorage.getFilesCreatedBefore(FileStorageNamespace.NOLOGIN, cutoff)
					.map { it.innsendingId }.toSet()
					.filter { !repo.existsByInnsendingsId(it) }

				logger.info("Found ${applications.size} unsubmitted nologin applications with files to delete")
				if (applications.isNotEmpty()) {
					val deleteCount = applications.sumOf { innsendingsId ->
						fileStorage.delete(FileStorageNamespace.NOLOGIN, innsendingsId.toUUID(), permanent = true)
					}
					logger.info("Deleted $deleteCount files associated with unsubmitted nologin applications")
				}

				logger.info("${javaClass.simpleName} done (elapsed time: ${jobTimer.getElapsedTimeMs()} ms)")
			}
		} catch (e: Exception) {
			logger.error("${javaClass.simpleName} failed: ${e.message}", e)
		}
	}

}
