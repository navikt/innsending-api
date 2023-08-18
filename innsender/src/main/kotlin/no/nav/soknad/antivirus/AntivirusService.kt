package no.nav.soknad.antivirus

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Service
class AntivirusService(private val antivirusWebClient: WebClient) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun scan(file: ByteArray): Boolean {
		logger.debug("Scanner dokument for virus")

		val response = try {
			antivirusWebClient.put()
				.bodyValue(file)
				.retrieve()
				.bodyToMono<List<ScanResult>>()
				.block()
		} catch (ex: Exception) {
			logger.error("Feil ved scanning for virus av dokument", ex)
			listOf(ScanResult("Unknown", ClamAvResult.ERROR))
		}

		if (response?.size != 1) {
			logger.warn("Feil størrelse på responsen fra virus scan")
			return false
		}

		val (filename, result) = response.first()
		logger.debug("$filename ${result.name}")

		return when (result) {
			ClamAvResult.OK -> true
			ClamAvResult.ERROR -> false

			ClamAvResult.FOUND -> {
				logger.warn("$filename har virus")
				false
			}

		}
	}
}
