package no.nav.soknad.innsending.supervision

import io.prometheus.client.*
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
@Component
class InnsenderMetrics(private val registry: CollectorRegistry) {

	// Skal telle opp antall opprettede, slettede og innsendte søknader pr tema
	private val soknadNamespace = "innsendingapi"
	private val appLabel = "soknadsinnsender"
	private val temaLabel = "tema"
	private val operationLabel = "operation"
	private val name = "number_of_operations"
	private val help = "Number of operations"
	private val appName = "app"
	private val errorName = "number_of_errors"
	private val helpError = "Number of errors"
	private val latency = "innsending_latency"
	private val latencyHelp = "Innsending latency distribution"
	private val databaseSizeName = "database_size"
	private val databaseSizeHelp = "Database size"
	private val fileNumberOfPages = "numberOfPagesInFile"
	private val fileNumberOfPagesHelp = "Numer of pages in an uploaded file"
	private val fileSize = "fileSize"
	private val fileSizeHelp = "Size of an uploaded file"
	private val absentInArchiveName = "applications_absent_in_archive_total"
	private val absentInArchiveHelp = "Number of sent in applications that not yet have been archived"
	private val archivingFailedName = "archiving_of_applications_failed_total"
	private val archivingFailedHelp = "Number of applications for which archiving failed"

	private val operationsCounter = registerCounter(name, help, operationLabel)
	private val operationsErrorCounter = registerCounter(errorName, helpError, operationLabel)
	private val operationLatencyHistogram = registerLatencyHistogram(latency, latencyHelp, operationLabel)
	private val databaseGauge = registerGauge(databaseSizeName, databaseSizeHelp)
	private val absentInArchiveGauge = registerGauge(absentInArchiveName, absentInArchiveHelp)
	private val archivingFailedGauge = registerGauge(archivingFailedName, archivingFailedHelp)
	private val fileNumberOfPagesSummary = registerSummary(fileNumberOfPages, fileNumberOfPagesHelp)
	private val fileSizeSummary = registerSummary(fileSize, fileSizeHelp)


	private val jobLastSuccessGauge = Gauge
		.build()
		.namespace(soknadNamespace)
		.name("job_last_success_timestamp")
		.help("Last time a job succeeded (unixtime)")
		.labelNames("job_name")
		.register(registry)

	private fun registerCounter(name: String, help: String, label: String): Counter =
		Counter
			.build()
			.namespace(soknadNamespace)
			.name(name)
			.help(help)
			.labelNames(label, temaLabel, appLabel)
			.register(registry)

	private fun registerLatencyHistogram(name: String, help: String, label: String): Histogram =
		Histogram
			.build()
			.namespace(soknadNamespace)
			.name(name)
			.help(help)
			.labelNames(label, appLabel)
			.buckets(0.1, 0.2, 0.4, 1.0, 2.0, 4.0, 15.0, 30.0, 60.0, 120.0)
			.register(registry)

	private fun registerGauge(name: String, help: String): Gauge =
		Gauge
			.build()
			.namespace(soknadNamespace)
			.name(name)
			.help(help)
			.register(registry)


	private fun registerSummary(name: String, help: String): Summary =
		Summary
			.build()
			.namespace(soknadNamespace)
			.name(name)
			.help(help)
			.quantile(0.99, 0.01)
			.quantile(0.50, 0.01)
			.quantile(0.40, 0.01)
			.quantile(0.30, 0.01)
			.quantile(0.20, 0.01)
			.quantile(0.10, 0.01)
			.maxAgeSeconds(10 * 60)
			.register(registry)


	fun incOperationsCounter(operation: String, tema: String) = operationsCounter.labels(operation, tema, appName).inc()
	fun getOperationsCounter(operation: String, tema: String) = operationsCounter.labels(operation, tema, appName)?.get()

	fun incOperationsErrorCounter(operation: String, tema: String) =
		operationsErrorCounter.labels(operation, tema, appName).inc()

	fun getOperationsErrorCounter(operation: String, tema: String) =
		operationsErrorCounter.labels(operation, tema, appName)?.get()

	fun startOperationHistogramLatency(operation: String): Histogram.Timer =
		operationLatencyHistogram.labels(operation, appName).startTimer()

	fun endOperationHistogramLatency(timer: Histogram.Timer) {
		timer.observeDuration()
	}

	fun getOperationHistogramLatency(operation: String): Histogram.Child.Value =
		operationLatencyHistogram.labels(operation, appName).get()

	fun setDatabaseSize(number: Long) = databaseGauge.set(number.toDouble())
	fun getDatabaseSize() = databaseGauge.get()

	fun setAbsentInArchive(number: Long) = absentInArchiveGauge.set(number.toDouble())

	fun setArchivingFailed(number: Long) = archivingFailedGauge.set(number.toDouble())

	fun updateJobLastSuccess(jobName: String) = jobLastSuccessGauge.labels(jobName).setToCurrentTime()

	fun setFileNumberOfPages(pages: Long) = fileNumberOfPagesSummary.observe(pages.toDouble())
	fun getFileNumberOfPages() = fileNumberOfPagesSummary.get()
	fun clearFileNumberOfPages() = fileNumberOfPagesSummary.clear()

	fun setFileSize(size: Long) = fileSizeSummary.observe(size.toDouble())
	fun getFileSize() = fileSizeSummary.get()
	fun clearFileSize() = fileSizeSummary.clear()


}
