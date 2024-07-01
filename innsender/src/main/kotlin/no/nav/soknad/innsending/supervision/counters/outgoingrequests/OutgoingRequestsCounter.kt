package no.nav.soknad.innsending.supervision.counters.outgoingrequests

import io.prometheus.metrics.core.metrics.Counter

import io.prometheus.metrics.model.registry.PrometheusRegistry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.collections.forEach
import kotlin.jvm.javaClass

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
		.labelNames("external_system", "method", "result")
		.register(registry)

	init {
		for (system in ExternalSystem.entries) {
			system.methods.forEach {
				instance.initLabelValues(system.id, it, MethodResult.CODE_OK.code)
				instance.initLabelValues(system.id, it, MethodResult.CODE_4XX.code)
				instance.initLabelValues(system.id, it, MethodResult.CODE_5XX.code)
			}
		}
	}

	fun inc(externalSystem: ExternalSystem, method: String, result: MethodResult = MethodResult.CODE_OK) {
		if (externalSystem.methods.contains(method)) {
			instance.labelValues(externalSystem.id, method, result.code).inc()
		} else {
			logger.warn("Metric: Unknown method $method for system $externalSystem")
		}
	}
}
