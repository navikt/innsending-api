package no.nav.soknad.innsending.consumerapis.antivirus

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Service
@Profile("test | dev | prod")
class AntivirusService(private val antivirusWebClient: WebClient) : AntivirusInterface {

	private val logger = LoggerFactory.getLogger(javaClass)

	override fun scan(file: ByteArray): Boolean {
		logger.info("Scanner dokument for virus")

		val response = try {
			antivirusWebClient.put()
				.uri("/scan")
				.bodyValue(file)
				.retrieve()
				.bodyToMono<List<ScanResult>>()
				.block()
		} catch (ex: Exception) {
			logger.error("Feil ved scanning for virus av dokument", ex)
			listOf(ScanResult("Unknown", ClamAvResult.ERROR))
		}

		if (response?.size != 1) {
			logger.error("Feil størrelse på responsen fra virus scan")
			return false
		}

		logger.info("Antivirus respons: $response, ${response.first()}")

		val (filename, result) = response.first()

		return when (result) {
			ClamAvResult.OK -> true
			ClamAvResult.ERROR -> true

			ClamAvResult.FOUND -> {
				logger.warn("$filename har virus")
				false
			}

		}
	}
}
