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
class ChaangeNotificationType(
	private val repo: RepositoryUtils,
	private val notificationService: NotificationService,
	private val leaderSelectionUtility: LeaderSelectionUtility
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	@Value("\${bidrag_applikasjon}")
	private lateinit var bidragApplikasjon: String

	@Scheduled(cron = "\${cron.runOnceAtSpecificTime}")
	fun findAllByApplikasjonAndChangeNotificationType() {
		logger.info("Skal finne og avslutte notifikasjoner og opprette nye")
		if (leaderSelectionUtility.isLeader()) {
			logger.info("Er leader")

			val fraTid = LocalDateTime.parse("2025-03-31T08:30:00")
			val tilTid = LocalDateTime.parse("2025-04-07T09:30:00")
			val findByApplicationType = repo.hentHendelseGittApplikasjon(bidragApplikasjon, HendelseType.Opprettet)
				.filter { it.tidspunkt.isAfter(fraTid) && it.tidspunkt.isBefore(tilTid) }
				.filter { it.innsendingsid == "301a3e73-c381-4784-bd20-28ea06cab13e" || it.innsendingsid == "74aaa380-dcfd-41c2-bd81-8f53b7e8ee7e" || it.innsendingsid == "73a03e46-98b9-417d-a29c-e8e7a9a7f5de" } // TODO remove

			logger.info("Antall søknder funnet som kanskje har fått feil notifikasjonstype")
			findByApplicationType.forEach { soknad -> findApplicationCloseAndOpenNotification(soknad.innsendingsid) }
		}
	}

	fun findApplicationCloseAndOpenNotification(innsendingId: String) {
		try {
			val soknad = repo.hentSoknadDb(innsendingId)
			logger.info("$innsendingId: funnet ? ${soknad != null}, status = ${soknad.status} Skal avslutte notifikasjon og opprette ny")
			if (soknad != null || soknad.status != SoknadsStatus.Opprettet) return

			logger.info("$innsendingId: Skal avslutte notifikasjon og opprette ny")
			notificationService.close(innsendingId)

			notificationService.create(innsendingId, NotificationOptions(erSystemGenerert = true, erNavInitiert = true))

		} catch (ex: Exception) {
			logger.error("Feil i innsendingId $innsendingId", ex)
		}
	}
}
