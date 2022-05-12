package no.nav.soknad.innsending.cleanup

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
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

	@OptIn(ExperimentalSerializationApi::class)
	val format = Json { explicitNulls = false; ignoreUnknownKeys = true }
	@Serializable
	data class LeaderElection(
		val name: String,
		val last_update: String? = LocalDateTime.now().toString()
	)

	val logger = LoggerFactory.getLogger(javaClass)

	@Value("\${cron.slettEldreEnn}")
	private lateinit var dagerGamleString: String

	@Scheduled(cron = "\${cron.slettGamleIkkeInnsendteSoknader}")
	fun fjernGamleIkkeInnsendteSoknader() {
		try {
			if (isLeader()) {
				soknadService.slettGamleIkkeInnsendteSoknader(dagerGamleString.toLong())
			}
		} catch (ex: Exception) {
			logger.warn("Fjerning av gamle ikke innsendte søknader feilet med ${ex.message}")
		}
	}

	fun isLeader(): Boolean {
		val hostname = InetAddress.getLocalHost().hostName
		val jsonString = fetchLeaderSelection()
		val leader = format.decodeFromString<LeaderElection>(jsonString).name

		val isLeader = hostname.equals(leader, true)
		logger.info("isLeader=$isLeader")
		return isLeader
	}

	fun fetchLeaderSelection(): String {
		val electorPath = System.getenv("ELECTOR_PATH") ?: System.getProperty("ELECTOR_PATH")
		if (electorPath.isNullOrBlank()) {
			logger.info("ELECTOR_PATH er null eller blank")
			throw RuntimeException("ELECTOR_PATH er null eller blank")
		}
		logger.info("Elector_path=$electorPath")
		val fullUrl = if (electorPath.contains(":/")) electorPath else "http://$electorPath"
		val jsonString = URL(fullUrl).readText()
		logger.info("Elector_path som jsonstring=$jsonString")
		return jsonString
	}

}
