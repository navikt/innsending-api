package no.nav.soknad.innsending.config

import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class RetryConfiguration {

	private val pdlRetryConfig: RetryConfig = RetryConfig
		.custom<RetryConfig>()
		.maxAttempts(2)
		.waitDuration(Duration.ofSeconds(3))
		.retryExceptions(RuntimeException::class.java)
		.build()

	private val safRetryConfig: RetryConfig = RetryConfig
		.custom<RetryConfig>()
		.maxAttempts(2)
		.waitDuration(Duration.ofSeconds(3))
		.retryExceptions(RuntimeException::class.java)
		.build()

	private val retryRegistry = RetryRegistry.of(mapOf("pdl" to pdlRetryConfig))

	@Bean
	fun retryPdl(): Retry = retryRegistry.retry("PDL", pdlRetryConfig)

	@Bean
	fun retrySaf(): Retry = retryRegistry.retry("SAF", safRetryConfig)
}
