package no.nav.soknad.innsending.supervision.counters

import io.prometheus.metrics.core.metrics.Counter

import io.prometheus.metrics.model.registry.PrometheusRegistry
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class OutgoingRequestsCounter(
	private val namespace: String,
	private val registry: PrometheusRegistry
) {
	private val logger: Logger = LoggerFactory.getLogger(javaClass)
	private val name = "outgoing_requests_total"
	private val help = "Number of requests to external system, including labels for method and result code"

	val instance = Counter.builder()
		.name("${namespace}_${name}")
		.help(help)
		.labelNames("external_system", "method", "result", "error")
		.register(registry)

	init {
		for (system in ExternalSystem.entries) {
			system.methods.forEach {
				instance.initLabelValues(ExternalSystem.ARENA.id, it, MethodResult.CODE_OK.code, "false")
				instance.initLabelValues(ExternalSystem.ARENA.id, it, MethodResult.CODE_4XX.code, "true")
				instance.initLabelValues(ExternalSystem.ARENA.id, it, MethodResult.CODE_5XX.code, "true")
			}
		}
	}

	fun inc(externalSystem: ExternalSystem, method: String, result: MethodResult = MethodResult.CODE_OK) {
		if (externalSystem.methods.contains(method)) {
			val error = if (result === MethodResult.CODE_OK) "false" else "true"
			instance.labelValues(externalSystem.id, method, result.code, error).inc()
		} else {
			logger.warn("Metric: Unknown method $method for system $externalSystem.id")
		}
	}
}
