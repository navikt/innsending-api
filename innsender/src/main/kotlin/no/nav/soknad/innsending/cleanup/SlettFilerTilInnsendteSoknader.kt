package no.nav.soknad.innsending.cleanup

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import no.nav.soknad.innsending.service.SoknadService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.net.InetAddress
import java.net.URL
import java.time.LocalDateTime

@Service
class SlettFilerTilInnsendteSoknader(private val soknadService: SoknadService) {

	val logger: Logger = LoggerFactory.getLogger(javaClass)


	@Value("\${cron.slettInnsendtFilEldreEnn}")
	private lateinit var slettInnsendtFilEldreEnn: String

	@Scheduled(cron = "\${cron.startSlettInnsendteFiler}")
	fun fjernFilerTilInnsendteSoknader() {
		try {
			if (LeaderSelectionUtility().isLeader()) {
				soknadService.slettfilerTilInnsendteSoknader(slettInnsendtFilEldreEnn.toInt())
			}
		} catch (ex: Exception) {
			logger.warn("Fjerning av filer for innsendte søknader feilet med ${ex.message}")
		}
	}


}
