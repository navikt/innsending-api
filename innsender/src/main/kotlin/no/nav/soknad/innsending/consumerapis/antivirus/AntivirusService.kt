package no.nav.soknad.innsending.consumerapis.antivirus

import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.io.InterruptedIOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

@Service
@Profile("dev | prod")
class AntivirusService(private val antivirusRestClient: RestClient, private val innsenderMetrics: InnsenderMetrics) :
	AntivirusInterface {

	private val logger = LoggerFactory.getLogger(javaClass)

	private val responseType = object : ParameterizedTypeReference<List<ScanResult>>() {}

	override fun scan(file: ByteArray): AntivirusScanResult = scanRequest(file).result

	override fun scanAttempt(file: ByteArray): AntivirusScanOutcome = try {
		scanRequest(file)
	} catch (ex: ResourceAccessException) {
		transportFailure(ex, AntivirusScanFailureReason.CONNECTION_ERROR)
	} catch (ex: RestClientException) {
		transportFailure(ex, AntivirusScanFailureReason.INVALID_RESPONSE)
	}

	private fun transportFailure(ex: RestClientException, fallback: AntivirusScanFailureReason): AntivirusScanOutcome {
		val causes = generateSequence<Throwable>(ex) { it.cause }.toList()
		val reason = when {
			Thread.currentThread().isInterrupted ||
				causes.any { it is InterruptedIOException && it !is SocketTimeoutException } -> {
				Thread.currentThread().interrupt()
				AntivirusScanFailureReason.INTERRUPTED
			}
			causes.any { it is SocketTimeoutException } -> AntivirusScanFailureReason.TIMEOUT
			causes.any { it is SSLException } -> AntivirusScanFailureReason.CONNECTION_REJECTED
			causes.any { it is SocketException || it is UnknownHostException } -> AntivirusScanFailureReason.CONNECTION_ERROR
			else -> fallback
		}
		return AntivirusScanOutcome(AntivirusScanResult.ERROR, reason, exceptionType = ex.mostSpecificCause.javaClass.simpleName)
	}

	private fun scanRequest(file: ByteArray): AntivirusScanOutcome {
		logger.debug("Starter scanning av dokument for virus")
		val histogramTimer = innsenderMetrics.startOperationHistogramLatency(InnsenderOperation.VIRUS_SCAN.name)
		try {
			return antivirusRestClient
				.put()
				.uri("/scan")
				.body(file)
				.exchange { _, response ->
					val status = response.statusCode.value()
					if (!response.statusCode.is2xxSuccessful) {
						val reason = if (status in setOf(500, 502, 503, 504)) {
							AntivirusScanFailureReason.HTTP_SERVER_ERROR
						} else {
							AntivirusScanFailureReason.HTTP_ERROR
						}
						AntivirusScanOutcome(AntivirusScanResult.ERROR, reason, status)
					} else {
						val results = response.bodyTo(responseType)
						if (results?.size != 1) {
							AntivirusScanOutcome(
								AntivirusScanResult.ERROR,
								AntivirusScanFailureReason.INVALID_RESPONSE,
								status,
							)
						} else {
							val result = when (results.first().result) {
								ClamAvResult.OK -> AntivirusScanResult.OK
								ClamAvResult.FOUND -> AntivirusScanResult.FOUND
								ClamAvResult.ERROR -> AntivirusScanResult.ERROR
							}
							AntivirusScanOutcome(result, httpStatus = status)
						}
					}
				}
				?: throw BackendErrorException("Ingen respons fra antivirus sjekken")
		} finally {
			innsenderMetrics.endOperationHistogramLatency(histogramTimer)
		}
	}
}
