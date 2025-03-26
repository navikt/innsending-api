package no.nav.soknad.innsending.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.util.Constants.AZURE
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.*
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.util.UriComponentsBuilder
import kotlin.test.assertEquals


class Api(val restTemplate: TestRestTemplate, val serverPort: Int, val mockOAuth2Server: MockOAuth2Server) {

	val baseUrl = "http://localhost:${serverPort}"
	val objectMapper: ObjectMapper = jacksonObjectMapper().findAndRegisterModules()

	private fun <T> createHttpEntity(body: T, map: Map<String, String>? = mapOf()): HttpEntity<T> {
		val token: String = TokenGenerator(mockOAuth2Server).lagTokenXToken()
		return HttpEntity(body, Hjelpemetoder.createHeaders(token, map))
	}

	private fun <T> createHttpEntity(body: T, map: Map<String, String>? = mapOf(), issuer: String): HttpEntity<T> {
		if (issuer != AZURE)	return createHttpEntity(body, map)
		val token: String = TokenGenerator(mockOAuth2Server).lagAzureToken()
		return HttpEntity(body, Hjelpemetoder.createHeaders(token, map))
	}

	fun createSoknad(skjemaDto: SkjemaDto, forceCreate: Boolean = true, envQualifier: EnvQualifier? = null): InnsendingApiResponse<SkjemaDto> {
		val headers: Map<String, String>? = if (envQualifier != null) mapOf(
			"Nav-Env-Qualifier" to envQualifier.value
		) else null
		val uri = UriComponentsBuilder.fromHttpUrl("${baseUrl}/fyllUt/v1/soknad")
			.queryParam("force", forceCreate)
			.build()
			.toUri()

		val response = restTemplate.exchange(uri, HttpMethod.POST, createHttpEntity(skjemaDto, headers), String::class.java)

		val body = readBody(response, SkjemaDto::class.java)
		return InnsendingApiResponse(response.statusCode, body, response.headers)
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

	fun getSoknadSendinn(innsendingsId: String): InnsendingApiResponse<DokumentSoknadDto> {
		val response = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${innsendingsId}",
			HttpMethod.GET,
			createHttpEntity(null),
			String::class.java
		)

		val body = readBody(response, DokumentSoknadDto::class.java)
		return InnsendingApiResponse(response.statusCode, body, response.headers)
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

	fun sendInnSoknad(innsendingsId: String, envQualifier: EnvQualifier? = null): InnsendingApiResponse<KvitteringsDto> {
		val headers: Map<String, String>? = if (envQualifier != null) mapOf(
			"Nav-Env-Qualifier" to envQualifier.value
		) else null
		val response = restTemplate.exchange(
			"${baseUrl}/frontend/v1/sendInn/${innsendingsId}",
			HttpMethod.POST,
			createHttpEntity(null, headers),
			String::class.java
		)

		val body = readBody(response, KvitteringsDto::class.java)
		return InnsendingApiResponse(response.statusCode, body, response.headers)
	}

	fun addVedlegg(innsendingsId: String, postVedleggDto: PostVedleggDto): InnsendingApiResponse<VedleggDto> {
		val response = restTemplate.exchange(
			"${baseUrl}/frontend/v1/soknad/${innsendingsId}/vedlegg",
			HttpMethod.POST,
			createHttpEntity(postVedleggDto),
			String::class.java
		)
		val body = readBody(response, VedleggDto::class.java)
		return InnsendingApiResponse(response.statusCode, body)
	}

	fun uploadFile(
		innsendingsId: String,
		vedleggsId: Long,
		file: ByteArray = Hjelpemetoder.getBytesFromFile("/litenPdf.pdf")
	): InnsendingApiResponse<FilDto> {
		val token: String = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val requestBody: MultiValueMap<String, ByteArray> = LinkedMultiValueMap()
		requestBody.add("file", file)

		val httpEntity = HttpEntity(requestBody, Hjelpemetoder.createHeaders(token, MediaType.MULTIPART_FORM_DATA))

		val response = restTemplate.exchange(
			"${baseUrl}/frontend/v1/soknad/${innsendingsId}/vedlegg/${vedleggsId}/fil",
			HttpMethod.POST,
			httpEntity,
			String::class.java
		)

		val body = readBody(response, FilDto::class.java)
		return InnsendingApiResponse(response.statusCode, body)
	}

	fun <T> readBody(response: ResponseEntity<String>, clazz: Class<T>): Pair<T?, RestErrorResponseDto?> {
		if (response.statusCode.is2xxSuccessful) {
			val body = objectMapper.readValue(
				response.body,
				clazz
			)
			return Pair(body, null)
		}
		val errorBody = objectMapper.readValue(response.body, RestErrorResponseDto::class.java)
		return Pair(null, errorBody)
	}

	fun createEttersending(opprettEttersending: OpprettEttersending, envQualifier: EnvQualifier? = null): InnsendingApiResponse<DokumentSoknadDto> {
		val headers: Map<String, String>? = if (envQualifier != null) mapOf(
			"Nav-Env-Qualifier" to envQualifier.value
		) else null
		val response = restTemplate.exchange(
			"${baseUrl}/fyllut/v1/ettersending",
			HttpMethod.POST,
			createHttpEntity(opprettEttersending, headers),
			String::class.java
		)

		val body = readBody(response, DokumentSoknadDto::class.java)
		return InnsendingApiResponse(response.statusCode, body, response.headers)
	}

	fun createEksternEttersending(eksternOpprettEttersending: EksternOpprettEttersending, envQualifier: EnvQualifier? = null): InnsendingApiResponse<DokumentSoknadDto> {
		val headers: Map<String, String>? = if (envQualifier != null) mapOf(
			"Nav-Env-Qualifier" to envQualifier.value
		) else null
		val response = restTemplate.exchange(
			"${baseUrl}/ekstern/v1/ettersending",
			HttpMethod.POST,
			createHttpEntity(eksternOpprettEttersending, headers),
			String::class.java
		)

		val body = readBody(response, DokumentSoknadDto::class.java)
		return InnsendingApiResponse(response.statusCode, body, response.headers)
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

	fun createLospost(
		opprettLospost: OpprettLospost,
		envQualifier: EnvQualifier? = null
	): InnsendingApiResponse<LospostDto> {
		val headers: Map<String, String>? = if (envQualifier != null) mapOf(
			"Nav-Env-Qualifier" to envQualifier.value
		) else null
		val requestEntity = createHttpEntity(opprettLospost, headers)
		val response = restTemplate.exchange(
			"${baseUrl}/fyllut/v1/lospost",
			HttpMethod.POST,
			requestEntity,
			String::class.java
		)

		val body = readBody(response, LospostDto::class.java)
		return InnsendingApiResponse(response.statusCode, body, response.headers)
	}

	fun getSoknaderForSkjemanr(
		skjemanr: String,
		soknadstyper: List<SoknadType>? = emptyList()
	): ResponseEntity<List<DokumentSoknadDto>> {
		val responseType = object : ParameterizedTypeReference<List<DokumentSoknadDto>>() {}
		var query = ""
		if (soknadstyper?.isNotEmpty() == true) {
			query = "?soknadstyper=${soknadstyper.joinToString()}"
		}
		return restTemplate.exchange(
			"${baseUrl}/ekstern/v1/skjema/${skjemanr}/soknader${query}",
			HttpMethod.GET,
			createHttpEntity(null),
			responseType
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


	fun createEttersendingsOppgave(opprettEttersendingsOppgave: EksternEttersendingsOppgave): ResponseEntity<DokumentSoknadDto> {
		val requestEntity = createHttpEntity(opprettEttersendingsOppgave, null, AZURE)
		return restTemplate.exchange(
			"${baseUrl}/ekstern/v1/oppgaver",
			HttpMethod.POST,
			requestEntity,
			DokumentSoknadDto::class.java
		)
	}

	fun eksternOppgaveSlett(innsendingsId: String): ResponseEntity<BodyStatusResponseDto> {
		val requestEntity = createHttpEntity(null, null, AZURE)
		return restTemplate.exchange(
			"${baseUrl}/ekstern/v1/oppgaver/${innsendingsId}",
			HttpMethod.DELETE,
			requestEntity,
			BodyStatusResponseDto::class.java
		)
	}

	fun eksternOppgaveSlettFail(innsendingsId: String): ResponseEntity<RestErrorResponseDto> {
		val requestEntity = createHttpEntity(null, null, AZURE)
		return restTemplate.exchange(
			"${baseUrl}/ekstern/v1/oppgaver/${innsendingsId}",
			HttpMethod.DELETE,
			requestEntity,
			RestErrorResponseDto::class.java
		)
	}


	fun oppgaveHentSoknaderForSkjemanr( skjemanr: String, brukerId: String, soknadstyper: List<SoknadType>?, navCallId: String?): ResponseEntity<List<DokumentSoknadDto>> {
		val requestEntity = createHttpEntity(BrukerSoknadRequest(brukerId = brukerId, skjemanr = skjemanr, soknadstyper = soknadstyper), null, AZURE)
		val responseType = object : ParameterizedTypeReference<List<DokumentSoknadDto>>() {}
		var query = ""
		if (soknadstyper?.isNotEmpty() == true) {
			query = "?soknadstyper=${soknadstyper.joinToString()}"
		}
		return restTemplate.exchange(
			"${baseUrl}/ekstern/v1/oppgaver",
			HttpMethod.GET,
			requestEntity,
			responseType
		)
	}

	data class InnsendingApiResponse<T>(
		val statusCode: HttpStatusCode,
		private val response: Pair<T?, RestErrorResponseDto?>,
		val headers: HttpHeaders? = null,
	) {
		val body: T
			get() {
				assertTrue(statusCode.is2xxSuccessful, "Expected success")
				return response.first!!
			}

		val errorBody: RestErrorResponseDto
			get() {
				assertFalse(statusCode.is2xxSuccessful, "Expected failure")
				return response.second!!
			}

		fun assertSuccess(): InnsendingApiResponse<T> {
			assertTrue(statusCode.is2xxSuccessful, "Expected successful response code")
			return this
		}

		fun assertClientError(): InnsendingApiResponse<T> {
			assertTrue(statusCode.is4xxClientError, "Expected client error")
			return this
		}

		fun assertHttpStatus(status: HttpStatus): InnsendingApiResponse<T> {
			assertEquals(status.value(), statusCode.value())
			return this
		}

		fun assertErrorCode(errorCode: ErrorCode): InnsendingApiResponse<T> {
			assertEquals(errorCode.code, errorBody.errorCode)
			return this
		}
	}

}
