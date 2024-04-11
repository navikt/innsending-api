package no.nav.soknad.innsending.consumerapis.skjema

import no.nav.soknad.innsending.config.RestConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.reactive.function.client.WebClient

@Service
class SkjemaClient(
	private val restConfig: RestConfig,
	@Qualifier("skjemaRestClient") private val restClient: RestClient
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun hent(): List<SkjemaOgVedleggsdata>? {
		logger.info("Hent skjema fra Sanity ${restConfig.sanityHost + restConfig.sanityEndpoint}")
		val skjemaer = restClient
			.get()
			.uri(restConfig.sanityEndpoint)
			.accept(MediaType.APPLICATION_JSON)
			.retrieve()
			.onStatus(HttpStatusCode::is4xxClientError ) { _, response ->
				run {
					val errorString =
						"Got ${response.statusCode} when requesting GET ${restConfig.sanityHost + restConfig.sanityEndpoint}"
					logger.error(errorString)
					throw RuntimeException(errorString)
				}
			}
			.onStatus(HttpStatusCode::is5xxServerError ) { _, response ->
				run {
					val errorString =
						"Got ${response.statusCode} when requesting GET ${restConfig.sanityHost + restConfig.sanityEndpoint}"
					logger.error(errorString)
					throw RuntimeException(errorString)
				}
			}
			.body(Skjemaer::class.java)

		return skjemaer?.skjemaer ?: emptyList()

	}

}
