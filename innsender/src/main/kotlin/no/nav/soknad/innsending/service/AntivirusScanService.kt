package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.consumerapis.antivirus.AntivirusInterface
import no.nav.soknad.innsending.consumerapis.antivirus.AntivirusScanResult
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.service.fillager.FileStorageNamespace
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AntivirusScanService(
	private val antivirus: AntivirusInterface,
	private val innsenderMetrics: InnsenderMetrics,
	@param:Qualifier("antivirusTaskExecutor") private val taskExecutor: TaskExecutor,
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
		if (result != AntivirusScanResult.OK) {
			throw IllegalActionException(
				message = "Opplasting feilet. Filen inneholder virus",
				errorCode = ErrorCode.VIRUS_SCAN_FAILED
			)
		}

		logger.info("$innsendingsId: Antiviruskontroll bestått for vedlegg $attachmentId")
	}

	fun scanAsynchronously(
		namespace: FileStorageNamespace,
		fileContent: ByteArray,
		innsendingsId: UUID,
		attachmentId: String,
		fileId: String,
	) {
		taskExecutor.execute {
			try {
				val result = antivirus.scan(fileContent)
				registerResult(namespace, AntivirusScanMode.ASYNCHRONOUS, result)
				when (result) {
					AntivirusScanResult.OK ->
						logger.info("$innsendingsId: Asynkron antiviruskontroll bestått for vedlegg $attachmentId (fileId=$fileId)")

					AntivirusScanResult.FOUND ->
						logger.warn("$innsendingsId: Asynkron antiviruskontroll fant virus for vedlegg $attachmentId (fileId=$fileId)")

					AntivirusScanResult.ERROR ->
						logger.warn("$innsendingsId: Asynkron antiviruskontroll mislyktes for vedlegg $attachmentId (fileId=$fileId)")
				}
			} catch (ex: Exception) {
				registerResult(namespace, AntivirusScanMode.ASYNCHRONOUS, AntivirusScanResult.ERROR)
				logger.warn(
					"$innsendingsId: Asynkron antiviruskontroll kastet feil for vedlegg $attachmentId (fileId=$fileId)",
					ex
				)
			}
		}
	}

	private fun registerResult(
		namespace: FileStorageNamespace,
		mode: AntivirusScanMode,
		result: AntivirusScanResult,
	) {
		innsenderMetrics.incAntivirusScanCounter(namespace.value, mode.value, result.metricValue)
	}
}
