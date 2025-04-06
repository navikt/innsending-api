package no.nav.soknad.innsending.supervision

import no.nav.soknad.innsending.brukernotifikasjon.NotificationOptions
import no.nav.soknad.innsending.cleanup.LeaderSelectionUtility
import no.nav.soknad.innsending.repository.domain.enums.HendelseType
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.service.NotificationService
import no.nav.soknad.innsending.service.RepositoryUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@EnableScheduling
@Component
class ChaangeNotificationType(private val repo: RepositoryUtils, private val notificationService: NotificationService) {

	private val logger = LoggerFactory.getLogger(javaClass)

	@Value("\${bidrag_applikasjon}")
	private lateinit var bidragApplikasjon: String

	@Scheduled(cron = "\${cron.runOnceAtSpecificTime}")
	fun findAllByApplikasjonAndChangeNotificationType() {
		if (LeaderSelectionUtility().isLeader()) {

			val fraTid = LocalDateTime.parse("2025-03-31T08:30:00")
			val tilTid = LocalDateTime.parse("2025-04-07T09:30:00")
			val findByApplicationType = repo.hentHendelseGittApplikasjon(bidragApplikasjon, HendelseType.Opprettet)
				.filter { it.tidspunkt.isAfter(fraTid) && it.tidspunkt.isBefore(tilTid) }
				.filter { it.innsendingsid == "301a3e73-c381-4784-bd20-28ea06cab13e" } // TODO remove

			findByApplicationType.forEach { soknad -> findApplicationCloseAndOpenNotification(soknad.innsendingsid) }
		}
	}

	fun findApplicationCloseAndOpenNotification(innsendingId: String) {
		try {
			val soknad = repo.hentSoknadDb(innsendingId)
			if (soknad != null || soknad.status != SoknadsStatus.Opprettet) return

			notificationService.close(innsendingId)

			notificationService.create(innsendingId, NotificationOptions(erSystemGenerert = true, erNavInitiert = true))

		} catch (ex: Exception) {
			logger.error("Feil i innsendingId $innsendingId", ex)
		}
	}
}
