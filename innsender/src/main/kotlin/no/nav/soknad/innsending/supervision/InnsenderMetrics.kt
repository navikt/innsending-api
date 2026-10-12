package no.nav.soknad.innsending.supervision

import io.prometheus.metrics.core.datapoints.Timer
import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.core.metrics.Gauge
import io.prometheus.metrics.core.metrics.Histogram
import io.prometheus.metrics.core.metrics.Summary
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.soknad.innsending.consumerapis.antivirus.AntivirusScanFailureReason
import no.nav.soknad.innsending.consumerapis.antivirus.AntivirusScanResult
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.service.AntivirusScanMode
import no.nav.soknad.innsending.service.fillager.FileStorageNamespace
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private const val missingVisningstype = "-"

@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
@Component
class InnsenderMetrics(private val registry: PrometheusRegistry) {

	// Skal telle opp antall opprettede, slettede og innsendte søknader pr tema
	private val soknadNamespace = "innsendingapi"
	private val appLabel = "soknadsinnsender"
	private val temaLabel = "tema"
	private val operationLabel = "operation"
	private val name = "number_of_operations_total"
	private val help = "Number of operations"
	private val appName = "app"
	private val errorName = "number_of_errors_total"
	private val helpError = "Number of errors"
	private val latency = "innsending_latency"
	private val latencyHelp = "Innsending latency distribution"
	private val databaseSizeName = "database_size"
	private val databaseSizeHelp = "Database size"
	private val fileNumberOfPages = "numberOfPagesInFile"
	private val fileNumberOfPagesHelp = "Numer of pages in an uploaded file"
	private val fileSize = "fileSize"
	private val fileSizeHelp = "Size of an uploaded file"
	private val absentInArchiveName = "applications_absent_in_archive"
	private val absentInArchiveHelp = "Number of sent in applications that not yet have been archived"
	private val archivingFailedName = "archiving_of_applications_failed"
	private val archivingFailedHelp = "Number of applications for which archiving failed"
	private val nologinMainSwitchName = "nologin_main_switch"
	private val nologinMainSwitchHelp = "Nologin main switch (on=1/off=0)"
	private val antivirusScanName = "antivirus_scan_total"
	private val antivirusScanHelp = "Number of antivirus scan outcomes"
	private val notSentInName = "applications_not_sent_in"
	private val notSentInHelp = "Number of applications that have not been sent in"

	private var operationsCounter = registerOperationCounter(name, help)
	private var operationsErrorCounter = registerOperationCounter(errorName, helpError)
	private var operationLatencyHistogram = registerLatencyHistogram(latency, latencyHelp, operationLabel)
	private var databaseGauge = registerGauge(databaseSizeName, databaseSizeHelp)
	private var absentInArchiveGauge = registerGauge(absentInArchiveName, absentInArchiveHelp)
	private var archivingFailedGauge = registerGauge(archivingFailedName, archivingFailedHelp)
	private var nologinGauge = registerGauge(nologinMainSwitchName, nologinMainSwitchHelp)
	private var antivirusScanCounter = registerAntivirusScanCounter()
	private var antivirusScanAttemptCounter = registerAntivirusScanAttemptCounter()
	private var antivirusScanRetryCounter = registerAntivirusScanRetryCounter()
	private var antivirusScanRecoveredCounter = registerAntivirusScanRecoveredCounter()
	private var antivirusScanDuration = registerAntivirusScanDuration()
	private var notSentInGauge = registerGauge("applications_not_sent_in", "Number of applications that have not been sent in")

	private var submissionsCounter = registerCounter(
		"submissions_total", "Number of submitted applications",
		listOf("visningstype")
	).also { counter ->
		val possibleLabelValues = VisningsType.entries.map { it.value }.toTypedArray() + missingVisningstype
		possibleLabelValues.forEach { counter.initLabelValues(it) }
	}

	var fileNumberOfPagesSummary = registerSummary(fileNumberOfPages, fileNumberOfPagesHelp)
	var fileSizeSummary = registerSummary(fileSize, fileSizeHelp)

	// Used in tests
	fun registerMetrics() {
		operationsCounter = registerOperationCounter(name, help)
		operationsErrorCounter = registerOperationCounter(errorName, helpError)
		operationLatencyHistogram = registerLatencyHistogram(latency, latencyHelp, operationLabel)
		databaseGauge = registerGauge(databaseSizeName, databaseSizeHelp)
		absentInArchiveGauge = registerGauge(absentInArchiveName, absentInArchiveHelp)
		archivingFailedGauge = registerGauge(archivingFailedName, archivingFailedHelp)
		nologinGauge = registerGauge(nologinMainSwitchName, nologinMainSwitchHelp)
		antivirusScanCounter = registerAntivirusScanCounter()
		antivirusScanAttemptCounter = registerAntivirusScanAttemptCounter()
		antivirusScanRetryCounter = registerAntivirusScanRetryCounter()
		antivirusScanRecoveredCounter = registerAntivirusScanRecoveredCounter()
		antivirusScanDuration = registerAntivirusScanDuration()
		fileNumberOfPagesSummary = registerSummary(fileNumberOfPages, fileNumberOfPagesHelp)
		fileSizeSummary = registerSummary(fileSize, fileSizeHelp)
		notSentInGauge = registerGauge(notSentInName, notSentInHelp)
	}

	// Used in tests
	fun unregisterMetrics() {
		registry.unregister(operationsCounter)
		registry.unregister(operationsErrorCounter)
		registry.unregister(operationLatencyHistogram)
		registry.unregister(databaseGauge)
		registry.unregister(absentInArchiveGauge)
		registry.unregister(archivingFailedGauge)
		registry.unregister(nologinGauge)
		registry.unregister(antivirusScanCounter)
		registry.unregister(antivirusScanAttemptCounter)
		registry.unregister(antivirusScanRetryCounter)
		registry.unregister(antivirusScanRecoveredCounter)
		registry.unregister(antivirusScanDuration)
		registry.unregister(fileNumberOfPagesSummary)
		registry.unregister(fileSizeSummary)
		registry.unregister(notSentInGauge)
	}

	private fun registerOperationCounter(name: String, help: String): Counter =
		registerCounter(name, help, listOf(operationLabel, temaLabel, appLabel))

	private fun registerCounter(name: String, help: String, labels: List<String>): Counter =
		Counter
			.builder()
			.name("${soknadNamespace}_${name}")
			.help(help)
			.labelNames(*labels.toTypedArray())
			.register(registry)

	private fun registerLatencyHistogram(name: String, help: String, label: String): Histogram =
		Histogram
			.builder()
			.classicExponentialUpperBounds(0.1, 2.0, 10)
			.name("${soknadNamespace}_${name}")
			.help(help)
			.labelNames(label, appLabel)
			.register(registry)

	private fun registerAntivirusScanDuration(): Histogram =
		Histogram.builder()
			.name("${soknadNamespace}_antivirus_scan_duration_seconds")
			.help("Asynchronous antivirus scan duration including retries and backoff")
			.labelNames("namespace", "result")
			.classicExponentialUpperBounds(0.1, 2.0, 13)
			.register(registry)

	private fun registerAntivirusScanCounter(): Counter =
		registerCounter(antivirusScanName, antivirusScanHelp, listOf("namespace", "mode", "result")).also { counter ->
			FileStorageNamespace.entries.forEach { namespace ->
				AntivirusScanMode.entries.forEach { mode ->
					AntivirusScanResult.entries.forEach { result ->
						counter.initLabelValues(namespace.value, mode.value, result.metricValue)
					}
				}
			}
		}

	private fun registerAntivirusScanAttemptCounter(): Counter =
		registerCounter(
			"antivirus_scan_attempts_total", "Number of asynchronous antivirus scan attempts", listOf("namespace", "result", "reason"),
		).also { counter ->
			FileStorageNamespace.entries.forEach { namespace ->
				counter.initLabelValues(namespace.value, "ok", "none")
				counter.initLabelValues(namespace.value, "found", "none")
				AntivirusScanFailureReason.entries.filter { it != AntivirusScanFailureReason.NONE }.forEach { reason ->
					counter.initLabelValues(namespace.value, "error", reason.metricValue)
				}
			}
		}

	private fun registerAntivirusScanRetryCounter(): Counter =
		registerCounter(
			"antivirus_scan_retries_total", "Number of additional asynchronous antivirus scan attempts", listOf("namespace", "reason"),
		).also { counter ->
			FileStorageNamespace.entries.forEach { namespace ->
				AntivirusScanFailureReason.entries.filter { it.retryable }.forEach { reason ->
					counter.initLabelValues(namespace.value, reason.metricValue)
				}
			}
		}

	private fun registerAntivirusScanRecoveredCounter(): Counter =
		registerCounter(
			"antivirus_scan_recovered_total", "Number of asynchronous antivirus scans completed after retry", listOf("namespace", "result"),
		).also { counter ->
			FileStorageNamespace.entries.forEach { namespace ->
				counter.initLabelValues(namespace.value, "ok")
				counter.initLabelValues(namespace.value, "found")
			}
		}

	private fun registerSummary(name: String, help: String): Summary =
		Summary
			.builder()
			.name("${soknadNamespace}_${name}")
			.help(help)
			.quantile(0.99, 0.01)
			.quantile(0.50, 0.01)
			.quantile(0.40, 0.01)
			.quantile(0.30, 0.01)
			.quantile(0.20, 0.01)
			.quantile(0.10, 0.01)
			.maxAgeSeconds(10 * 60)
			.register(registry)

	private fun registerGauge(name: String, help: String): Gauge =
		Gauge
			.builder()
			.name("${soknadNamespace}_${name}")
			.help(help)
			.register(registry)


	fun incOperationsCounter(operation: String, tema: String) =
		operationsCounter.labelValues(operation, tema, appName).inc()

	fun getOperationsCounter(operation: String, tema: String) =
		operationsCounter.labelValues(operation, tema, appName)?.get()

	fun incOperationsErrorCounter(operation: String, tema: String) =
		operationsErrorCounter.labelValues(operation, tema, appName).inc()

	fun incSubmissionsCounter(visningstype: VisningsType?) =
		submissionsCounter.labelValues(visningstype?.value ?: missingVisningstype).inc()

	fun startOperationHistogramLatency(operation: String): Timer? =
		operationLatencyHistogram.labelValues(operation, appName).startTimer()

	fun endOperationHistogramLatency(timer: Timer?) {
		timer?.observeDuration()
	}

	fun setDatabaseSize(number: Long) = databaseGauge.set(number.toDouble())

	fun setAbsentInArchive(number: Long) = absentInArchiveGauge.set(number.toDouble())

	fun setArchivingFailed(number: Long) = archivingFailedGauge.set(number.toDouble())

	fun setNologinMainSwitch(number: Long) = nologinGauge.set(number.toDouble())
	fun incAntivirusScanCounter(namespace: String, mode: String, result: String) =
		antivirusScanCounter.labelValues(namespace, mode, result).inc()

	fun getAntivirusScanCounter(namespace: String, mode: String, result: String) =
		antivirusScanCounter.labelValues(namespace, mode, result)?.get()

	fun incAntivirusScanAttemptCounter(namespace: String, result: String, reason: String) =
		antivirusScanAttemptCounter.labelValues(namespace, result, reason).inc()

	fun getAntivirusScanAttemptCounter(namespace: String, result: String, reason: String) =
		antivirusScanAttemptCounter.labelValues(namespace, result, reason).get()

	fun incAntivirusScanRetryCounter(namespace: String, reason: String) =
		antivirusScanRetryCounter.labelValues(namespace, reason).inc()

	fun getAntivirusScanRetryCounter(namespace: String, reason: String) =
		antivirusScanRetryCounter.labelValues(namespace, reason).get()

	fun incAntivirusScanRecoveredCounter(namespace: String, result: String) =
		antivirusScanRecoveredCounter.labelValues(namespace, result).inc()

	fun getAntivirusScanRecoveredCounter(namespace: String, result: String) =
		antivirusScanRecoveredCounter.labelValues(namespace, result).get()

	fun observeAntivirusScanDuration(namespace: String, result: String, seconds: Double) =
		antivirusScanDuration.labelValues(namespace, result).observe(seconds)

	fun setFileNumberOfPages(pages: Long) = fileNumberOfPagesSummary.observe(pages.toDouble())
	fun clearFileNumberOfPages() = fileNumberOfPagesSummary.clear()

	fun setFileSize(size: Long) = fileSizeSummary.observe(size.toDouble())
	fun clearFileSize() = fileSizeSummary.clear()

	fun setNotSentIn(number: Long) = notSentInGauge.set(number.toDouble())

}
