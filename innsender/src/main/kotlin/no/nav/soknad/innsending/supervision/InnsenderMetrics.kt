package no.nav.soknad.innsending.supervision

import io.prometheus.metrics.core.datapoints.Timer
import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.core.metrics.Gauge
import io.prometheus.metrics.core.metrics.Histogram
import io.prometheus.metrics.core.metrics.Summary
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.soknad.innsending.supervision.counters.OutgoingRequestsCounter
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

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

	private var operationsCounter = registerCounter(name, help, operationLabel)
	private var operationsErrorCounter = registerCounter(errorName, helpError, operationLabel)
	private var operationLatencyHistogram = registerLatencyHistogram(latency, latencyHelp, operationLabel)
	private var databaseGauge = registerGauge(databaseSizeName, databaseSizeHelp)
	private var absentInArchiveGauge = registerGauge(absentInArchiveName, absentInArchiveHelp)
	private var archivingFailedGauge = registerGauge(archivingFailedName, archivingFailedHelp)

	var outgoingRequestsCounter = OutgoingRequestsCounter(soknadNamespace, registry)
	var fileNumberOfPagesSummary = registerSummary(fileNumberOfPages, fileNumberOfPagesHelp)
	var fileSizeSummary = registerSummary(fileSize, fileSizeHelp)

	// Used in tests
	fun registerMetrics() {
		operationsCounter = registerCounter(name, help, operationLabel)
		operationsErrorCounter = registerCounter(errorName, helpError, operationLabel)
		operationLatencyHistogram = registerLatencyHistogram(latency, latencyHelp, operationLabel)
		databaseGauge = registerGauge(databaseSizeName, databaseSizeHelp)
		absentInArchiveGauge = registerGauge(absentInArchiveName, absentInArchiveHelp)
		archivingFailedGauge = registerGauge(archivingFailedName, archivingFailedHelp)
		outgoingRequestsCounter = OutgoingRequestsCounter(soknadNamespace, registry)
		fileNumberOfPagesSummary = registerSummary(fileNumberOfPages, fileNumberOfPagesHelp)
		fileSizeSummary = registerSummary(fileSize, fileSizeHelp)
	}

	// Used in tests
	fun unregisterMetrics() {
		registry.unregister(operationsCounter)
		registry.unregister(operationsErrorCounter)
		registry.unregister(operationLatencyHistogram)
		registry.unregister(databaseGauge)
		registry.unregister(absentInArchiveGauge)
		registry.unregister(archivingFailedGauge)
		registry.unregister(outgoingRequestsCounter.instance)
		registry.unregister(fileNumberOfPagesSummary)
		registry.unregister(fileSizeSummary)
	}

	private fun registerCounter(name: String, help: String, label: String): Counter =
		Counter
			.builder()
			.name("${soknadNamespace}_${name}")
			.help(help)
			.labelNames(label, temaLabel, appLabel)
			.register(registry)

	private fun registerLatencyHistogram(name: String, help: String, label: String): Histogram =
		Histogram
			.builder()
			.classicExponentialUpperBounds(0.1, 2.0, 10)
			.name("${soknadNamespace}_${name}")
			.help(help)
			.labelNames(label, appLabel)
			.register(registry)

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

	fun startOperationHistogramLatency(operation: String): Timer? =
		operationLatencyHistogram.labelValues(operation, appName).startTimer()

	fun endOperationHistogramLatency(timer: Timer?) {
		timer?.observeDuration()
	}

	fun setDatabaseSize(number: Long) = databaseGauge.set(number.toDouble())

	fun setAbsentInArchive(number: Long) = absentInArchiveGauge.set(number.toDouble())

	fun setArchivingFailed(number: Long) = archivingFailedGauge.set(number.toDouble())

	fun setFileNumberOfPages(pages: Long) = fileNumberOfPagesSummary.observe(pages.toDouble())
	fun clearFileNumberOfPages() = fileNumberOfPagesSummary.clear()

	fun setFileSize(size: Long) = fileSizeSummary.observe(size.toDouble())
	fun clearFileSize() = fileSizeSummary.clear()

}
