package no.nav.soknad.innsending.consumerapis.pdl

import io.github.resilience4j.kotlin.retry.executeFunction
import io.github.resilience4j.retry.Retry
import no.nav.soknad.innsending.consumerapis.pdl.dto.OidcToken
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
@Profile("dev | prod")
class StsClient(
	private val stsWebClient: WebClient,
	private val retrySts: Retry
) {

	private var cachedOidcToken: OidcToken? = null

	companion object {
		@Suppress("JAVA_CLASS_ON_COMPANION")
		private val logger = LoggerFactory.getLogger(javaClass.enclosingClass)
	}

	fun oidcToken(): String {

		if (cachedOidcToken.shouldBeRenewed()) {
			logger.debug("Getting token from STS")
			runCatching {
				retrySts.executeFunction {
					cachedOidcToken = stsWebClient.get()
						.uri { uriBuilder ->
							uriBuilder
								.queryParam("grant_type", "client_credentials")
								.queryParam("scope", "openid")
								.build()
						}
						.retrieve()
						.bodyToMono<OidcToken>()
						.block()
				}
			}.onFailure {
				throw RuntimeException("STS could not be reached")
			}
		}

		return cachedOidcToken!!.token
	}

	private fun OidcToken?.shouldBeRenewed(): Boolean = this?.hasExpired() ?: true
}

