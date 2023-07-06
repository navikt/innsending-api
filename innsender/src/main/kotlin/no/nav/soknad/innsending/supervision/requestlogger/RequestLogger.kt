package no.nav.soknad.innsending.supervision.requestlogger

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import kotlin.reflect.full.memberProperties

// Log the request and response of a method annotated with @LogRequest. Example: @LogRequest
// LogParameters is a list of parameters to log from the request object. Example: @LogRequest("skjemanr")
@Aspect
@Component
class RequestLogger {
	private val logger = LoggerFactory.getLogger(javaClass)

	@Around("@annotation(logRequest)")
	@Throws(Throwable::class)
	fun logRequest(joinPoint: ProceedingJoinPoint, logRequest: LogRequest) {
		val requestAttributes = RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes
		val request = requestAttributes.request

		try {
			val logParameters = logRequest.logParameters.toList()
			val params = mutableMapOf<String, String>()
			joinPoint.args.forEach { arg ->
				val kClass = arg::class
				for (prop in kClass.memberProperties) {
					if (prop.name in logParameters) {
						params[prop.name] = prop.getter.call(arg).toString()
					}
				}
			}

			if (params.isEmpty()) {
				logger.info("Request: [${joinPoint.signature.name}] ${request.method} ${request.requestURI}")
			} else {
				logger.info("Request: [${joinPoint.signature.name}] ${request.method} ${request.requestURI}. Parameters: $params")
			}

			joinPoint.proceed()
		} catch (ex: Exception) {
			logger.warn("Kunne ikke logge request fra annotation", ex)
		} finally {
			val response = requestAttributes.response
			logger.info("Response: [${joinPoint.signature.name}] ${request.method} ${request.requestURI}. Status: (${response?.status})")
		}
	}
}
