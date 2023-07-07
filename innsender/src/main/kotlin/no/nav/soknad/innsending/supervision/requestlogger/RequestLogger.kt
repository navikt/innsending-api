package no.nav.soknad.innsending.supervision.requestlogger

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

// Log the request and response of a method annotated with @LogRequest. Example: @LogRequest
// LogParameters is a list of parameters to log from the request object. Example: @LogRequest("skjemanr")
@Aspect
@Component
class RequestLogger {
	private val logger = LoggerFactory.getLogger(javaClass)

	fun isFromPackage(kClass: KClass<*>, packageName: String): Boolean {
		return kClass.qualifiedName?.startsWith(packageName) ?: false
	}

	@Around("@annotation(logRequest)")
	@Throws(Throwable::class)
	fun logRequest(joinPoint: ProceedingJoinPoint, logRequest: LogRequest): Any? {
		val requestAttributes = RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes
		val request = requestAttributes.request

		try {
			val logParameters = logRequest.logParameters.toList()
			val params = mutableMapOf<String, String>()
			joinPoint.args.forEach { arg ->
				val kClass = arg::class
				if (!isFromPackage(kClass, "no.nav.soknad.innsending")) return@forEach

				for (prop in kClass.memberProperties) {
					if (prop.name in logParameters) {
						params[prop.name] = prop.call(arg).toString()
					}
				}
			}

			if (params.isEmpty()) {
				logger.info("Request: ${request.method} ${request.requestURI} | [${joinPoint.signature.name}] ")
			} else {
				logger.info("Request: ${request.method} ${request.requestURI} | [${joinPoint.signature.name}] | Parameters: $params")
			}

			return joinPoint.proceed()
		} finally {
			val response = requestAttributes.response
			logger.info("Response: ${request.method} ${request.requestURI} | [${joinPoint.signature.name}] | Status: (${response?.status})")
		}
	}
}
