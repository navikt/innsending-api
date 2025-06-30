package no.nav.soknad.innsending.consumerapis.antivirus

import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.core.ParameterizedTypeReference

@Service
@Profile("dev | prod")
class AntivirusService(private val antivirusRestClient: RestClient, private val innsenderMetrics: InnsenderMetrics) :
	AntivirusInterface {

	private val logger = LoggerFactory.getLogger(javaClass)

	val responseType = object: ParameterizedTypeReference<List<ScanResult>>() {}


	override fun scan(file: ByteArray): Boolean {
		val startTime = System.currentTimeMillis()
		logger.info("Starter scanning av dokument for virus")

		val histogramTimer = innsenderMetrics.startOperationHistogramLatency(InnsenderOperation.VIRUS_SCAN.name)
		try {
			val response =
				antivirusRestClient
					.put()
					.uri("/scan")
					.body(file)
					.exchange { request, response ->
						run {
							if (response.statusCode.isError) {
								logger.error("Feil ved scanning for virus av dokument ({})", response.statusCode)
								listOf(ScanResult("Unknown", ClamAvResult.ERROR))
							} else {
								response.bodyTo(responseType) ?: listOf(ScanResult("Unknown", ClamAvResult.ERROR))
							}
						}
					}


			logger.info("Virus scanning brukte ${System.currentTimeMillis() - startTime}ms på å fullføre")

			if (response == null) {
				throw BackendErrorException("Ingen respons fra antivirus sjekken")
			}

			if (response.size != 1) {
				logger.error("Feil størrelse på responsen fra virus scan")
				return false
			}

			logger.info("Antivirus respons: $response")

			val (filename, result) = response.first()

			return when (result) {
				ClamAvResult.OK -> true
				ClamAvResult.ERROR -> {
					logger.warn("$filename kunne ikke skannes for virus")
					false
				}
				ClamAvResult.FOUND -> {
					logger.warn("$filename har virus")
					false
				}

			}
		} finally {
			innsenderMetrics.endOperationHistogramLatency(histogramTimer)

		}
	}
}
