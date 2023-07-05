package no.nav.soknad.innsending.cleanup

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.net.URL

@Component
class LeaderSelectionUtility {
	val logger: Logger = LoggerFactory.getLogger(javaClass)

	@OptIn(ExperimentalSerializationApi::class)
	val format = Json { explicitNulls = false; ignoreUnknownKeys = true }


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
