package no.nav.soknad.innsending.supervision

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Histogram
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
			.buckets(100.0, 200.0, 400.0, 1000.0, 2000.0, 4000.0, 15000.0, 30000.0)
			.register(registry)

	private fun registerGauge(name: String, help: String): Gauge =
		Gauge
			.build()
			.namespace(soknadNamespace)
			.name(name)
			.help(help)
			.register(registry)


	fun operationsCounterInc(operation: String, tema: String) = operationsCounter.labels(operation, tema, appName).inc()
	fun operationsCounterGet(operation: String, tema: String) = operationsCounter.labels(operation, tema, appName)?.get()

	fun operationsErrorCounterInc(operation: String, tema: String) =
		operationsErrorCounter.labels(operation, tema, appName).inc()

	fun operationsErrorCounterGet(operation: String, tema: String) =
		operationsErrorCounter.labels(operation, tema, appName)?.get()

	fun operationHistogramLatencyStart(operation: String): Histogram.Timer =
		operationLatencyHistogram.labels(operation, appName).startTimer()

	fun operationHistogramLatencyEnd(timer: Histogram.Timer) {
		timer.observeDuration()
	}

	fun operationHistogramGetLatency(operation: String): Histogram.Child.Value =
		operationLatencyHistogram.labels(operation, appName).get()

	fun databaseSizeSet(number: Long) = databaseGauge.set(number.toDouble())
	fun databaseSizeGet() = databaseGauge.get()

	fun absentInArchive(number: Long) = absentInArchiveGauge.set(number.toDouble())

	fun archivingFailedSet(number: Long) = archivingFailedGauge.set(number.toDouble())

	fun updateJobLastSuccess(jobName: String) = jobLastSuccessGauge.labels(jobName).setToCurrentTime()
}
