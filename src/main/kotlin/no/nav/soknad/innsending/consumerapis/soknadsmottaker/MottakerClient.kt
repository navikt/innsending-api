package no.nav.soknad.innsending.consumerapis.soknadsmottaker

import no.nav.soknad.arkivering.soknadsMottaker.dto.SoknadInnsendtDto
import no.nav.soknad.innsending.config.AppConfiguration
import org.apache.tomcat.util.codec.binary.Base64
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient

@Service
class MottakerClient(private val appConfiguration: AppConfiguration,
										 @Qualifier("authClient") private val webClient: WebClient
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	fun sendInn(soknad: SoknadInnsendtDto) {
		val uri = appConfiguration.restConfig.soknadsMottakerHost + appConfiguration.restConfig.soknadsMottakerEndpoint
		val method = HttpMethod.POST
		val webClient = setupWebClient(uri, method)

		webClient
			.body(BodyInserters.fromValue(soknad))
			.retrieve()
			.onStatus(
				{ httpStatus -> httpStatus.is4xxClientError || httpStatus.is5xxServerError },
				{ response -> response.bodyToMono(String::class.java).map {
					Exception("Got ${response.statusCode()} when requesting $method $uri - response body: '$it'")
				}
				})
	}

	private fun setupWebClient(uri: String, method: HttpMethod): WebClient.RequestBodySpec {

		val auth = "${appConfiguration.restConfig.username}:${appConfiguration.restConfig.password}"
		val encodedAuth: ByteArray = Base64.encodeBase64(auth.toByteArray())
		val authHeader = "Basic " + String(encodedAuth)

		return webClient
			.method(method)
			.uri(uri)
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON)
			.header("Authorization", authHeader)
	}

}
