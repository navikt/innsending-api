package no.nav.soknad.innsending.cleanup

import no.nav.soknad.innsending.service.SoknadService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class SlettArkiverteSoknader(
	private val leaderSelectionUtility: LeaderSelectionUtility,
	private val soknadService: SoknadService
) {

	val logger: Logger = LoggerFactory.getLogger(javaClass)


	@Value("\${cron.slettInnsendtEldreEnn}")
	private lateinit var slettArkiverteSoknaderEldreEnn: String

	@Value("\${cron.sletteVindu}")
	private lateinit var vindu: String

	@Scheduled(cron = "\${cron.startSlettInnsendteSoknader}")
	fun fjernArkiverteSoknader() {
		try {
			if (leaderSelectionUtility.isLeader()) {
				val startTime = System.currentTimeMillis()
				soknadService.finnOgSlettArkiverteSoknader(slettArkiverteSoknaderEldreEnn.toLong(), vindu.toLong())
				val executionTime = System.currentTimeMillis() - startTime
				logger.info("Fjerning av arkiverte søknader fullført på $executionTime ms")
			}
		} catch (ex: Exception) {
			logger.error("Fjerning av arkiverte søknader feilet med $ex", ex)
		}
	}


}
