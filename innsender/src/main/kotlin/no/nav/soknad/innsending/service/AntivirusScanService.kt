package no.nav.soknad.innsending.service

import io.github.resilience4j.retry.Retry
import no.nav.soknad.innsending.consumerapis.antivirus.AntivirusInterface
import no.nav.soknad.innsending.consumerapis.antivirus.AntivirusScanFailureReason
import no.nav.soknad.innsending.consumerapis.antivirus.AntivirusScanOutcome
import no.nav.soknad.innsending.consumerapis.antivirus.AntivirusScanResult
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.service.fillager.FileStorageNamespace
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.CancellationException

@Service
class AntivirusScanService(
	private val antivirus: AntivirusInterface,
	private val innsenderMetrics: InnsenderMetrics,
	@Qualifier("retryAntivirus") private val retryAntivirus: Retry,
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	fun scanSynchronously(
		namespace: FileStorageNamespace,
		fileContent: ByteArray,
		innsendingsId: UUID,
		attachmentId: String,
	) {
		val result = try {
			antivirus.scan(fileContent)
		} catch (ex: Exception) {
			registerResult(namespace, AntivirusScanMode.SYNCHRONOUS, AntivirusScanResult.ERROR)
			throw ex
		}

		registerResult(namespace, AntivirusScanMode.SYNCHRONOUS, result)
		if (result == AntivirusScanResult.ERROR) {
			logger.error("$innsendingsId: Synkron antiviruskontroll mislyktes for vedlegg $attachmentId")
		}
		if (result != AntivirusScanResult.OK) {
			throw IllegalActionException(
				message = "Opplasting feilet. Filen inneholder virus",
				errorCode = ErrorCode.VIRUS_SCAN_FAILED
			)
		}

		logger.info("$innsendingsId: Antiviruskontroll bestått for vedlegg $attachmentId")
	}

	@Async
	fun scanAsynchronously(
		namespace: FileStorageNamespace,
		fileContent: ByteArray,
		innsendingsId: UUID,
		attachmentId: String,
		fileId: String,
	) {
		val startTime = System.nanoTime()
		var attempts = 0
		var outcome = AntivirusScanOutcome(AntivirusScanResult.ERROR, AntivirusScanFailureReason.UNEXPECTED_ERROR)
		try {
			outcome = retryAntivirus.executeSupplier {
				if (Thread.currentThread().isInterrupted) throw InterruptedException()
				if (attempts > 0) {
					innsenderMetrics.incAntivirusScanRetryCounter(namespace.value, outcome.failureReason.metricValue)
				}
				attempts++
				outcome = try {
					antivirus.scanAttempt(fileContent)
				} catch (ex: Exception) {
					val reason = if (ex is InterruptedException || Thread.currentThread().isInterrupted) {
						Thread.currentThread().interrupt()
						AntivirusScanFailureReason.INTERRUPTED
					} else {
						AntivirusScanFailureReason.UNEXPECTED_ERROR
					}
					AntivirusScanOutcome(AntivirusScanResult.ERROR, reason, exceptionType = ex.javaClass.simpleName)
				}
				innsenderMetrics.incAntivirusScanAttemptCounter(
					namespace.value, outcome.result.metricValue, outcome.failureReason.metricValue,
				)
				if (outcome.retryable && attempts < retryAntivirus.retryConfig.maxAttempts) {
					logger.atWarn()
						.addKeyValue("scan_namespace", namespace.value)
						.addKeyValue("innsendingsId", innsendingsId)
						.addKeyValue("attachmentId", attachmentId)
						.addKeyValue("fileId", fileId)
						.addKeyValue("scan_attempt", attempts)
						.addKeyValue("scan_failure_reason", outcome.failureReason.metricValue)
						.addKeyValue("http_status", outcome.httpStatus)
						.log("Asynkron antiviruskontroll feilet midlertidig; prøver igjen med backoff")
				}
				outcome
			}
		} catch (ex: InterruptedException) {
			Thread.currentThread().interrupt()
			outcome = AntivirusScanOutcome(AntivirusScanResult.ERROR, AntivirusScanFailureReason.INTERRUPTED)
		} catch (ex: CancellationException) {
			outcome = AntivirusScanOutcome(AntivirusScanResult.ERROR, AntivirusScanFailureReason.INTERRUPTED)
		} catch (ex: Exception) {
			outcome = AntivirusScanOutcome(
				AntivirusScanResult.ERROR, AntivirusScanFailureReason.UNEXPECTED_ERROR, exceptionType = ex.javaClass.simpleName,
			)
		} finally {
			val elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0
			registerResult(namespace, AntivirusScanMode.ASYNCHRONOUS, outcome.result)
			innsenderMetrics.observeAntivirusScanDuration(namespace.value, outcome.result.metricValue, elapsedSeconds)
		}

		if (attempts > 1 && outcome.result != AntivirusScanResult.ERROR) {
			innsenderMetrics.incAntivirusScanRecoveredCounter(namespace.value, outcome.result.metricValue)
		}
		val log = when {
			outcome.failureReason == AntivirusScanFailureReason.INTERRUPTED -> logger.atWarn()
			outcome.result == AntivirusScanResult.ERROR -> logger.atError()
			outcome.result == AntivirusScanResult.FOUND -> logger.atWarn()
			else -> logger.atInfo()
		}
		val message = when {
			outcome.failureReason == AntivirusScanFailureReason.INTERRUPTED -> "Asynkron antiviruskontroll avbrutt"
			outcome.result == AntivirusScanResult.ERROR -> "Asynkron antiviruskontroll mislyktes"
			outcome.result == AntivirusScanResult.FOUND -> "Asynkron antiviruskontroll fant virus"
			attempts > 1 -> "Asynkron antiviruskontroll bestått etter retry"
			else -> "Asynkron antiviruskontroll bestått"
		}
		log.addKeyValue("scan_namespace", namespace.value)
			.addKeyValue("innsendingsId", innsendingsId)
			.addKeyValue("attachmentId", attachmentId)
			.addKeyValue("fileId", fileId)
			.addKeyValue("scan_attempts", attempts)
			.addKeyValue("scan_result", outcome.result.metricValue)
			.addKeyValue("scan_failure_reason", outcome.failureReason.metricValue)
			.addKeyValue("http_status", outcome.httpStatus)
			.addKeyValue("exception_type", outcome.exceptionType)
			.addKeyValue("scan_duration_ms", (System.nanoTime() - startTime) / 1_000_000)
			.log(message)
	}

	private fun registerResult(
		namespace: FileStorageNamespace,
		mode: AntivirusScanMode,
		result: AntivirusScanResult,
	) {
		innsenderMetrics.incAntivirusScanCounter(namespace.value, mode.value, result.metricValue)
	}
}
