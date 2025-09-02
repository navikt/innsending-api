package no.nav.soknad.innsending.supervision.timer

import no.nav.soknad.innsending.supervision.InnsenderMetrics
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Aspect
@Component
@Order(10)
class Timer(private val innsenderMetrics: InnsenderMetrics) {
	private val logger = LoggerFactory.getLogger(javaClass)

	@Around("@annotation(timed)")
	@Throws(Throwable::class)
	fun timer(joinPoint: ProceedingJoinPoint, timed: Timed): Any? {
		val startTime = System.currentTimeMillis()
		val histogramTimer = innsenderMetrics.startOperationHistogramLatency(timed.operation.name)

		try {
			return joinPoint.proceed()
		} finally {
			innsenderMetrics.endOperationHistogramLatency(histogramTimer)

			val method = "${joinPoint.signature.declaringTypeName}.${joinPoint.signature.name}()"
			logger.debug("$method took ${System.currentTimeMillis() - startTime}ms to complete")
		}
	}
}
