package no.nav.soknad.innsending.utils

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.model.*
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.util.UriComponentsBuilder


class Api(val restTemplate: TestRestTemplate, val serverPort: Int, val mockOAuth2Server: MockOAuth2Server) {

	val baseUrl = "http://localhost:${serverPort}"

	private fun <T> createHttpEntity(body: T): HttpEntity<T> {
		val token: String = TokenGenerator(mockOAuth2Server).lagTokenXToken()
		return HttpEntity(body, Hjelpemetoder.createHeaders(token))
	}

	fun createSoknad(skjemaDto: SkjemaDto, forceCreate: Boolean = true): ResponseEntity<SkjemaDto> {
		val uri = UriComponentsBuilder.fromHttpUrl("${baseUrl}/fyllUt/v1/soknad")
			.queryParam("force", forceCreate)
			.build()
			.toUri()

		return restTemplate.exchange(uri, HttpMethod.POST, createHttpEntity(skjemaDto), SkjemaDto::class.java)
	}

	fun createSoknadForSkjemanr(skjemanr: String, spraak: String = "nb_NO"): ResponseEntity<DokumentSoknadDto> {
		val opprettSoknadBody = OpprettSoknadBody(skjemanr, spraak)
		return restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad",
			HttpMethod.POST,
			createHttpEntity(opprettSoknadBody),
			DokumentSoknadDto::class.java
		)
	}

	fun createSoknadRedirect(
		skjemaDto: SkjemaDto,
		forceCreate: Boolean = true
	): ResponseEntity<BodyStatusResponseDto> {
		val uri = UriComponentsBuilder.fromHttpUrl("${baseUrl}/fyllUt/v1/soknad")
			.queryParam("force", forceCreate)
			.build()
			.toUri()

		return restTemplate.exchange(uri, HttpMethod.POST, createHttpEntity(skjemaDto), BodyStatusResponseDto::class.java)
	}

	fun updateSoknad(innsendingsId: String, skjemaDto: SkjemaDto): ResponseEntity<SkjemaDto>? {
		return restTemplate.exchange(
			"${baseUrl}/fyllUt/v1/soknad/${innsendingsId}",
			HttpMethod.PUT,
			createHttpEntity(skjemaDto),
			SkjemaDto::class.java
		)
	}

	fun updateSoknadFail(innsendingsId: String, skjemaDto: SkjemaDto): ResponseEntity<RestErrorResponseDto>? {
		return restTemplate.exchange(
			"${baseUrl}/fyllUt/v1/soknad/${innsendingsId}",
			HttpMethod.PUT,
			createHttpEntity(skjemaDto),
			RestErrorResponseDto::class.java
		)
	}

	fun deleteSoknad(innsendingsId: String): ResponseEntity<BodyStatusResponseDto>? {
		return restTemplate.exchange(
			"http://localhost:${serverPort}/fyllUt/v1/soknad/${innsendingsId}",
			HttpMethod.DELETE,
			createHttpEntity(null),
			BodyStatusResponseDto::class.java
		)
	}

	fun getSoknad(innsendingsId: String): ResponseEntity<SkjemaDto>? {
		return restTemplate.exchange(
			"${baseUrl}/fyllUt/v1/soknad/${innsendingsId}",
			HttpMethod.GET,
			createHttpEntity(null),
			SkjemaDto::class.java
		)
	}

	fun getSoknadSendinn(innsendingsId: String): ResponseEntity<DokumentSoknadDto>? {
		return restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${innsendingsId}",
			HttpMethod.GET,
			createHttpEntity(null),
			DokumentSoknadDto::class.java
		)
	}

	// Query param ex: "soknad,ettersendelse"
	fun getExistingSoknader(skjemanr: String, queryParam: String? = null): ResponseEntity<List<DokumentSoknadDto>>? {
		val url = if (queryParam != null) {
			"http://localhost:${serverPort}/frontend/v1/skjema/${skjemanr}/soknader?soknadstyper=$queryParam"
		} else {
			"http://localhost:${serverPort}/frontend/v1/skjema/${skjemanr}/soknader"
		}

		val responseType = object : ParameterizedTypeReference<List<DokumentSoknadDto>>() {}
		return restTemplate.exchange(
			url,
			HttpMethod.GET,
			createHttpEntity(null),
			responseType
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

	fun addVedlegg(innsendingsId: String, postVedleggDto: PostVedleggDto): ResponseEntity<VedleggDto>? {
		return restTemplate.exchange(
			"${baseUrl}/frontend/v1/soknad/${innsendingsId}/vedlegg",
			HttpMethod.POST,
			createHttpEntity(postVedleggDto),
			VedleggDto::class.java
		)
	}

	fun uploadFile(
		innsendingsId: String,
		vedleggsId: Long,
		file: ByteArray = Hjelpemetoder.getBytesFromFile("/litenPdf.pdf")
	): ResponseEntity<FilDto> {
		val token: String = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val requestBody: MultiValueMap<String, ByteArray> = LinkedMultiValueMap()
		requestBody.add("file", file)

		val httpEntity = HttpEntity(requestBody, Hjelpemetoder.createHeaders(token, MediaType.MULTIPART_FORM_DATA))

		return restTemplate.exchange(
			"${baseUrl}/frontend/v1/soknad/${innsendingsId}/vedlegg/${vedleggsId}/fil",
			HttpMethod.POST,
			httpEntity,
			FilDto::class.java
		)
	}

	fun createEttersending(opprettEttersending: OpprettEttersending): ResponseEntity<DokumentSoknadDto> {
		return restTemplate.exchange(
			"${baseUrl}/fyllut/v1/ettersending",
			HttpMethod.POST,
			createHttpEntity(opprettEttersending),
			DokumentSoknadDto::class.java
		)
	}

	fun createEksternEttersending(eksternOpprettEttersending: EksternOpprettEttersending): ResponseEntity<DokumentSoknadDto> {
		return restTemplate.exchange(
			"${baseUrl}/ekstern/v1/ettersending",
			HttpMethod.POST,
			createHttpEntity(eksternOpprettEttersending),
			DokumentSoknadDto::class.java
		)
	}

	fun deleteEksternEttersending(innsendingsId: String): ResponseEntity<BodyStatusResponseDto> {
		return restTemplate.exchange(
			"${baseUrl}/ekstern/v1/ettersending/${innsendingsId}",
			HttpMethod.DELETE,
			createHttpEntity(null),
			BodyStatusResponseDto::class.java
		)
	}

	fun deleteEksternEttersendingFail(innsendingsId: String): ResponseEntity<RestErrorResponseDto> {
		return restTemplate.exchange(
			"${baseUrl}/ekstern/v1/ettersending/${innsendingsId}",
			HttpMethod.DELETE,
			createHttpEntity(null),
			RestErrorResponseDto::class.java
		)
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

	fun getAktiviteter(aktivitetEndepunkt: AktivitetEndepunkt): ResponseEntity<List<Aktivitet>>? {
		val responseType = object : ParameterizedTypeReference<List<Aktivitet>>() {}

		val dagligReise = if (aktivitetEndepunkt == AktivitetEndepunkt.dagligreise) "true" else "false"
		return restTemplate.exchange(
			"${baseUrl}/fyllUt/v1/aktiviteter?dagligreise=${dagligReise}",
			HttpMethod.GET,
			createHttpEntity(null),
			responseType
		)
	}
}
