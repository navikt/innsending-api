package no.nav.soknad.innsending.supervision

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Histogram
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
@Component
class InnsenderMetrics(private val registry: CollectorRegistry) {

	// Skal telle opp antall opprettede, slettede og innsendte søknader pr tema
	private val soknadNamespace = "soknadinnsending"
	private val appLabel = "soknadsinnsender"
	private val temaLabel = "tema"
	private val operationLabel = "operation"
	private val name = "number_of_applications"
	private val help = "Number of applications"
	private val appName = "app"
	private val errorName = "number_of_errors"
	private val helpError = "Number of errors"
	private val latency = "innsending_latency"
	private val latencyHelp = "Innsending latency distribution"

	private val applicationCounter = registerCounter(name, help, operationLabel)
	private val applicationErrorCounter = registerCounter(errorName, helpError, operationLabel)

	private val operationLatencyHistogram = registerLatencyHistogram(latency, latencyHelp, operationLabel)


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


	fun applicationCounterInc(operation: String, tema: String) = applicationCounter.labels(operation, tema, appName).inc()
	fun applicationCounterGet(operation: String, tema: String) = applicationCounter.labels(operation, tema, appName)?.get()

	fun applicationErrorCounterInc(operation: String, tema: String) = applicationErrorCounter.labels(operation, tema, appName).inc()
	fun applicationErrorCounterGet(operation: String, tema: String) = applicationErrorCounter.labels(operation, tema, appName)?.get()

	fun operationHistogramLatencyStart(operation: String): Histogram.Timer =  operationLatencyHistogram.labels(operation, appName).startTimer()
	fun operationHistogramLatencyEnd(timer: Histogram.Timer) {timer.observeDuration()}
	fun operationHistogramGetLatency(operation: String): Histogram.Child.Value = operationLatencyHistogram.labels(operation, appName).get()


}
