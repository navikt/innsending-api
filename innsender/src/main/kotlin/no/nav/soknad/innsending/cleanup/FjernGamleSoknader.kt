package no.nav.soknad.innsending.cleanup

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import no.nav.soknad.innsending.service.SoknadService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.net.InetAddress
import java.net.URL
import java.time.LocalDateTime

@Service
class FjernGamleSoknader(private val soknadService: SoknadService) {

	private val logger = LoggerFactory.getLogger(javaClass)

	@Value("\${cron.slettEldreEnn}")
	private lateinit var dagerGamleString: String

	@Scheduled(cron = "\${cron.slettGamleIkkeInnsendteSoknader}")
	fun fjernGamleIkkeInnsendteSoknader() {
		if (isLeader()) {
			soknadService.slettGamleIkkeInnsendteSoknader(dagerGamleString.toLong())
		}
	}

	private fun isLeader(): Boolean {
		logger.info("Sjekk om leader")
		val electorPath = System.getenv("ELECTOR_PATH") ?: System.getProperty("ELECTOR_PATH")
		if (electorPath.isNullOrBlank()) {
			logger.info("ELECTOR_PATH er null eller blank")
			return false
		}
		try {
			logger.info("Elector_path=$electorPath")
			val fullUrl = if (electorPath.contains(":/")) electorPath else "http://$electorPath"
			val jsonString = URL(fullUrl).readText()
			logger.info("Elector_path som jsonstring=$jsonString")
			val format = Json { encodeDefaults = true }
			val leader = format.decodeFromString<LeaderElection>(jsonString).name

			val hostname = InetAddress.getLocalHost().hostName

			logger.info("isLeader=${hostname.equals(leader, true)}")
			return hostname.equals(leader, true)

		} catch (exception: Exception) {
			logger.warn("Sjekk om leader feilet med:", exception)
			return false
		}
	}

	@Serializable
	data class LeaderElection(
		val name: String,
		val lastUpdate: LocalDateTime
	)
}
