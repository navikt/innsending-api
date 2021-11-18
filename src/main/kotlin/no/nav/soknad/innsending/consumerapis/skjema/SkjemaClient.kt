package no.nav.soknad.innsending.consumerapis.skjema

import no.nav.soknad.innsending.config.AppConfiguration
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType

@Service
class SkjemaClient(private val appConfiguration: AppConfiguration,
									 @Qualifier("basicClient") private val webClient: WebClient
) {

	fun hent(): List<SkjemaOgVedleggsdata>? {
			return webClient
				.get()
				.uri(appConfiguration.restConfig.sanityHost+appConfiguration.restConfig.sanityEndpoint)
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.onStatus(
					{ httpStatus -> httpStatus.is4xxClientError || httpStatus.is5xxServerError },
						{ response -> response.bodyToMono(String::class.java).map {
								Exception("Got ${response.statusCode()} when requesting GET ${appConfiguration.restConfig.sanityHost} - response body: '$it'")
						}
					}
				)
				.bodyToFlux(SkjemaOgVedleggsdata::class.java)
				.collectList()
				.block()
	}

}
