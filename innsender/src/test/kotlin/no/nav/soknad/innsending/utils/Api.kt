package no.nav.soknad.innsending.utils

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.dto.RestErrorResponseDto
import no.nav.soknad.innsending.model.*
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.util.UriComponentsBuilder

class Api(val restTemplate: TestRestTemplate, val serverPort: Int, val mockOAuth2Server: MockOAuth2Server) {

	val baseUrl = "http://localhost:${serverPort}"

	private fun <T> createHttpEntity(body: T): HttpEntity<T> {
		val token: String = TokenGenerator(mockOAuth2Server).lagTokenXToken()
		return HttpEntity(body, Hjelpemetoder.createHeaders(token))
	}

	fun opprettSoknad(skjemaDto: SkjemaDto, opprettNySoknad: Boolean = true): ResponseEntity<SkjemaDto> {
		val uri = UriComponentsBuilder.fromHttpUrl("${baseUrl}/fyllUt/v1/soknad")
			.queryParam("opprettNySoknad", opprettNySoknad)
			.build()
			.toUri()

		return restTemplate.exchange(uri, HttpMethod.POST, createHttpEntity(skjemaDto), SkjemaDto::class.java)
	}

	fun oppdaterSoknad(innsendingsId: String, skjemaDto: SkjemaDto): ResponseEntity<SkjemaDto>? {
		return restTemplate.exchange(
			"${baseUrl}/fyllUt/v1/soknad/${innsendingsId}",
			HttpMethod.PUT,
			createHttpEntity(skjemaDto),
			SkjemaDto::class.java
		)
	}

	fun slettSoknad(innsendingsId: String): ResponseEntity<BodyStatusResponseDto>? {
		return restTemplate.exchange(
			"http://localhost:${serverPort}/fyllUt/v1/soknad/${innsendingsId}",
			HttpMethod.DELETE,
			createHttpEntity(null),
			BodyStatusResponseDto::class.java
		)
	}

	fun hentSoknad(innsendingsId: String): ResponseEntity<SkjemaDto>? {
		return restTemplate.exchange(
			"${baseUrl}/fyllUt/v1/soknad/${innsendingsId}",
			HttpMethod.GET,
			createHttpEntity(null),
			SkjemaDto::class.java
		)
	}

	fun utfyltSoknad(innsendingsId: String, skjemaDto: SkjemaDto): ResponseEntity<Unit> {
		return restTemplate.exchange(
			"${baseUrl}/fyllUt/v1/utfyltSoknad/${innsendingsId}",
			HttpMethod.PUT,
			createHttpEntity(skjemaDto),
			Unit::class.java
		)
	}

	fun sendInnSoknad(innsendingsId: String): ResponseEntity<KvitteringsDto> {
		return restTemplate.exchange(
			"${baseUrl}/frontend/v1/sendInn/${innsendingsId}",
			HttpMethod.POST,
			createHttpEntity(null),
			KvitteringsDto::class.java
		)
	}

	fun leggTilVedlegg(innsendingsId: String, postVedleggDto: PostVedleggDto): ResponseEntity<VedleggDto>? {
		return restTemplate.exchange(
			"${baseUrl}/frontend/v1/soknad/${innsendingsId}/vedlegg",
			HttpMethod.POST,
			createHttpEntity(postVedleggDto),
			VedleggDto::class.java
		)
	}

	fun hentAktiveSaker(): ResponseEntity<List<AktivSakDto>>? {
		return restTemplate.exchange(
			"${baseUrl}/innsendte/v1/hentAktiveSaker",
			HttpMethod.GET,
			createHttpEntity(null),
			object : ParameterizedTypeReference<List<AktivSakDto>>() {})
	}

	fun getPrefillData(properties: String): ResponseEntity<PrefillData>? {
		return restTemplate.exchange(
			"${baseUrl}/fyllUt/v1/prefill-data?properties=$properties",
			HttpMethod.GET,
			createHttpEntity(null),
			PrefillData::class.java
		)
	}

	fun getPrefillDataFail(properties: String): ResponseEntity<RestErrorResponseDto>? {
		return restTemplate.exchange(
			"${baseUrl}/fyllUt/v1/prefill-data?properties=$properties",
			HttpMethod.GET,
			createHttpEntity(null),
			RestErrorResponseDto::class.java
		)
	}
}
