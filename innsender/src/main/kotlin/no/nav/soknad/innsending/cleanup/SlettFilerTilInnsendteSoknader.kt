package no.nav.soknad.innsending.cleanup

import no.nav.soknad.innsending.service.FilService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class SlettFilerTilInnsendteSoknader(
	private val leaderSelectionUtility: LeaderSelectionUtility,
	private val filService: FilService
) {

	val logger: Logger = LoggerFactory.getLogger(javaClass)


	@Value("\${cron.slettInnsendtFilEldreEnn}")
	private lateinit var slettInnsendtFilEldreEnn: String

	@Scheduled(cron = "\${cron.startSlettInnsendteFiler}")
	fun fjernFilerTilInnsendteSoknader() {
		try {
			if (leaderSelectionUtility.isLeader()) {
				filService.slettfilerTilInnsendteSoknader(slettInnsendtFilEldreEnn.toInt())
			}
		} catch (ex: Exception) {
			logger.warn("Fjerning av filer for innsendte søknader feilet med ${ex.message}")
		}
	}


}
