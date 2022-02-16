package no.nav.soknad.innsending.supervision

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
@Component
class InnsenderMetrics(private val registry: CollectorRegistry) {

	private val logger = LoggerFactory.getLogger(javaClass)

	// Skal telle opp antall opprettede, slettede og innsendte søknader pr tema
	private val soknadNamespace = "soknadinnsending"
	private val app_label = "soknadsinnsender"
	private val tema_label = "tema"
	private val operation_label = "operation"
	private val name = "number_of_applications"
	private val help = "Number of applications"
	private val appName = "app"
	private val errorName = "number_of_errors"
	private val helpError = "Number of errors"
	private val error = "error"
	private val ok = "ok"
	private val all = "all"

	private val applicationCounter = registerCounter(name, help, operation_label)
	private val applicationErrorCounter = registerCounter(errorName, helpError, operation_label)


	private fun registerCounter(name: String, help: String, label: String): Counter =
		Counter
			.build()
			.namespace(soknadNamespace)
			.name(name)
			.help(help)
			.labelNames(label, tema_label, app_label)
			.register(registry)

	fun applicationCounterInc(operation: String, tema: String) = applicationCounter.labels(operation, tema, app_label).inc()
	fun applicationCounterGet(operation: String, tema: String) = applicationCounter.labels(operation, tema, app_label)?.get()

	fun applicationErrorCounterInc(operation: String, tema: String) = applicationErrorCounter.labels(operation, tema, app_label).inc()
	fun applicationErrorCounterGet(operation: String, tema: String) = applicationErrorCounter.labels(operation, tema, app_label)?.get()

}
