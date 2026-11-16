package no.nav.soknad.innsending.consumerapis.texas

import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import org.slf4j.LoggerFactory
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientResponseException
import java.time.Duration

/**
 * Ett nytt forsøk mot Texas ved forbigående feil (#461): nettverksfeil, tidsavbrudd og 502, 503 eller 504 fra Texas.
 * Andre feil, som 4xx og ugyldige svar, blir ikke bedre av et nytt forsøk.
 *
 * Loggen viser bare kallet og feiltypen, aldri meldingen fra unntaket, fordi den kan inneholde svaret fra Texas.
 */
internal object TexasRetry {

	val DEFAULT_WAIT: Duration = Duration.ofMillis(200)
	private const val MAX_ATTEMPTS = 2
	private val TRANSIENT_STATUSES = setOf(502, 503, 504)

	private val logger = LoggerFactory.getLogger(TexasRetry::class.java)

	fun create(name: String, wait: Duration): Retry =
		Retry.of(
			name,
			RetryConfig.custom<Any>()
				.maxAttempts(MAX_ATTEMPTS)
				.waitDuration(wait)
				.retryOnException(::isTransient)
				.build(),
		).also { retry ->
			retry.eventPublisher.onRetry { event ->
				logger.warn("Prøver $name mot Texas på nytt etter forbigående feil (${failure(event.lastThrowable)})")
			}
		}

	fun isTransient(e: Throwable): Boolean =
		e is ResourceAccessException ||
			(e is RestClientResponseException && e.statusCode.value() in TRANSIENT_STATUSES)

	private fun failure(e: Throwable?): String = when (e) {
		is RestClientResponseException -> "status ${e.statusCode.value()}"
		null -> "ukjent"
		else -> e.javaClass.simpleName
	}
}
