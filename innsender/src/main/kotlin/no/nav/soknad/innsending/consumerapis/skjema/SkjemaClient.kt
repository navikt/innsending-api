package no.nav.soknad.innsending.consumerapis.skjema

import no.nav.soknad.innsending.config.RestConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType

@Service
class SkjemaClient(private val restConfig: RestConfig,
									 @Qualifier("basicClient") private val webClient: WebClient
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun hent(): List<SkjemaOgVedleggsdata>? {
			logger.info("Hent skjema fra Sanity ${restConfig.sanityHost+restConfig.sanityEndpoint}")
			val skjemaer = webClient
				.get()
				.uri(restConfig.sanityHost+restConfig.sanityEndpoint)
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.onStatus(
					{ httpStatus -> httpStatus.is4xxClientError || httpStatus.is5xxServerError },
						{ response -> response.bodyToMono(String::class.java).map {
								Exception("Got ${response.statusCode()} when requesting GET ${restConfig.sanityHost} - response body: '$it'")
						}
					}
				)
				.bodyToFlux(Skjemaer::class.java)
				.collectList()
				.block()

		logger.info("Skjema hentet fra Sanity ${skjemaer}")

		if (skjemaer != null && skjemaer.get(0) != null && skjemaer.get(0).skjemaer!!.isNotEmpty()) {
				return skjemaer.get(0)?.skjemaer ?: emptyList()
			}
			throw RuntimeException("Feil ved forsøk på henting av skjema fra sanity")

	}

}