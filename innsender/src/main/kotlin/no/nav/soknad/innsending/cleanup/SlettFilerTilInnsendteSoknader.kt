package no.nav.soknad.innsending.cleanup

import no.nav.soknad.innsending.service.SoknadService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class SlettFilerTilInnsendteSoknader(private val soknadService: SoknadService, private val leaderSelectionUtility: LeaderSelectionUtility) {

	val logger: Logger = LoggerFactory.getLogger(javaClass)


	@Value("\${cron.slettInnsendtFilEldreEnn}")
	private lateinit var slettInnsendtFilEldreEnn: String

	@Scheduled(cron = "\${cron.startSlettInnsendteFiler}")
	fun fjernFilerTilInnsendteSoknader() {
		try {
			if (leaderSelectionUtility.isLeader()) {
				soknadService.slettfilerTilInnsendteSoknader(slettInnsendtFilEldreEnn.toInt())
			}
		} catch (ex: Exception) {
			logger.warn("Fjerning av filer for innsendte søknader feilet med ${ex.message}")
		}
	}


}
