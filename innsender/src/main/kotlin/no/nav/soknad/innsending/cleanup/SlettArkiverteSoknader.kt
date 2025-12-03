package no.nav.soknad.innsending.cleanup

import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.util.Timer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class SlettArkiverteSoknader(
	private val leaderSelectionUtility: LeaderSelection,
	private val soknadService: SoknadService
) {

	val logger: Logger = LoggerFactory.getLogger(javaClass)


	@Value("\${cron.slettInnsendtEldreEnn}")
	private lateinit var slettArkiverteSoknaderEldreEnn: String

	@Value("\${cron.sletteVindu}")
	private lateinit var vindu: String

	@Value("\${cron.maxAntallSoknader}")
	private lateinit var maxAntallSoknader: String

	@Scheduled(cron = "\${cron.startSlettInnsendteSoknader}")
	fun fjernArkiverteSoknader() {
		try {
			if (leaderSelectionUtility.isLeader()) {
				val jobTimer = Timer.start()
				val arkiverteSoknader =
					soknadService.finnArkiverteSoknader(slettArkiverteSoknaderEldreEnn.toLong(), vindu.toLong())

				val maxAntall = maxAntallSoknader.toInt()
				val antallSomSkalSlettesNaa = arkiverteSoknader.size.coerceAtMost(maxAntall)
				logger.info("Fant ${arkiverteSoknader.size} arkiverte søknader som skal slettes, vil slette $antallSomSkalSlettesNaa nå")
				arkiverteSoknader.take(maxAntall)
					.chunked(500)
					.forEachIndexed { index, soknader ->
						val batchTimer = Timer.start()
						soknadService.slettSoknaderPermanent(soknader.map { it.innsendingsid })
						logger.info("Slettet batch nr. ${index.plus(1)} med ${soknader.size} søknad(er) på ${batchTimer.getElapsedTimeMs()} ms")
					}

				logger.info("Fjerning av arkiverte søknader fullført på ${jobTimer.getElapsedTimeMs()} ms (totalt $antallSomSkalSlettesNaa søknader slettet)")
			}
		} catch (ex: Exception) {
			logger.error("Fjerning av arkiverte søknader feilet med $ex", ex)
		}
	}


}
