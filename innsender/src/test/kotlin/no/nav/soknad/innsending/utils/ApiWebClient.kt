package no.nav.soknad.innsending.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.model.Aktivitet
import no.nav.soknad.innsending.model.AktivitetEndepunkt
import no.nav.soknad.innsending.model.ApplicationSubmissionResponse
import no.nav.soknad.innsending.model.AttachmentDto
import no.nav.soknad.innsending.model.AvsenderDto
import no.nav.soknad.innsending.model.BodyStatusResponseDto
import no.nav.soknad.innsending.model.BrukerSoknadRequest
import no.nav.soknad.innsending.model.ConfigValueDto
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.EksternEttersendingsOppgave
import no.nav.soknad.innsending.model.EksternOpprettEttersending
import no.nav.soknad.innsending.model.EnvQualifier
import no.nav.soknad.innsending.model.FilDto
import no.nav.soknad.innsending.model.FileDto
import no.nav.soknad.innsending.model.KvitteringsDto
import no.nav.soknad.innsending.model.LastOppFilResponse
import no.nav.soknad.innsending.model.LospostDto
import no.nav.soknad.innsending.model.OpprettEttersending
import no.nav.soknad.innsending.model.OpprettLospost
import no.nav.soknad.innsending.model.OpprettSoknadBody
import no.nav.soknad.innsending.model.PatchVedleggDto
import no.nav.soknad.innsending.model.PostVedleggDto
import no.nav.soknad.innsending.model.PrefillData
import no.nav.soknad.innsending.model.RestErrorResponseDto
import no.nav.soknad.innsending.model.RunJobRequest
import no.nav.soknad.innsending.model.SetConfigRequest
import no.nav.soknad.innsending.model.SkjemaDto
import no.nav.soknad.innsending.model.SkjemaDtoV2
import no.nav.soknad.innsending.model.SoknadFile
import no.nav.soknad.innsending.model.SoknadType
import no.nav.soknad.innsending.model.SubmitApplicationRequest
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.service.config.ConfigDefinition
import no.nav.soknad.innsending.util.Constants.AZURE
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult
import org.springframework.util.CollectionUtils
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.util.UriComponentsBuilder
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals

class ApiWebClient(val webTestClient_: WebTestClient, val serverPort: Int, val mockOAuth2Server: MockOAuth2Server) {

	val baseUrl = "http://localhost:${serverPort}"
	val objectMapper: ObjectMapper = jacksonObjectMapper().findAndRegisterModules()
	val webTestClient = webTestClient_.mutate().responseTimeout(Duration.ofMinutes(2L)).baseUrl(baseUrl).build()

	private fun <T : Any> createHttpEntity(body: T, map: Map<String, String>? = mapOf(), authToken: String? = null): HttpEntity<T> {
		val token: String = authToken ?: TokenGenerator(mockOAuth2Server).lagTokenXToken()
		return HttpEntity(body, Hjelpemetoder.createHeaders(token, map))
	}


	fun setConfig(config: ConfigDefinition, string: String?, authToken: String? = null): InnsendingApiResponse<ConfigValueDto> {
		val token = authToken ?: TokenGenerator(mockOAuth2Server).lagAzureOBOToken(scopes = "admin-access", navIdent = "Z123456")
		val response = webTestClient.put()
			.uri("${baseUrl}/v1/config/${config.key}")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(token, null).toSingleValueMap())
			})
			.bodyValue(SetConfigRequest(string) ?: "" )
			.exchange()
		return InnsendingApiResponse(response.returnResult().status, readBody(response, ConfigValueDto::class.java), response.returnResult().responseHeaders)
	}

	fun getConfig(config: ConfigDefinition, authToken: String? = null): InnsendingApiResponse<ConfigValueDto> {
		val token = authToken ?: TokenGenerator(mockOAuth2Server).lagAzureOBOToken(scopes = "admin-access", navIdent = "Z123456")
		val response = webTestClient.get()
			.uri("${baseUrl}/v1/config/${config.key}")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(token, null).toSingleValueMap())
			})
			.exchange()
		val body = readBody(response, ConfigValueDto::class.java)
		return InnsendingApiResponse(response.returnResult().status, body, response.returnResult().responseHeaders)
	}


	fun <T> readBody( response: WebTestClient.ResponseSpec, clazz: Class<T>): Pair<T?, RestErrorResponseDto?> {
		if (response.returnResult().status.is2xxSuccessful) {
			val body = objectMapper.readValue(
				response.returnResult().responseBodyContent,
				clazz
			)
			return Pair(body, null)
		}
		val errorBody = objectMapper.readValue(response.returnResult().responseBodyContent, RestErrorResponseDto::class.java)
		return Pair(null, errorBody)
	}


	fun createSoknad(skjemaDto: SkjemaDto, forceCreate: Boolean = true, envQualifier: EnvQualifier? = null): InnsendingApiResponse<SkjemaDto> {
		val headers: Map<String, String>? = if (envQualifier != null) mapOf(
			"Nav-Env-Qualifier" to envQualifier.value
		) else null
		val uri = UriComponentsBuilder.fromUriString("${baseUrl}/fyllUt/v1/soknad")
			.queryParam("force", forceCreate)
			.build()
			.toUri()

		val response = webTestClient.post()
			.uri(uri)
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagTokenXToken(), headers).toSingleValueMap())
			})
			.bodyValue(skjemaDto)
			.exchange()

		return InnsendingApiResponse(response.returnResult().status, readBody(response,SkjemaDto::class.java), response.returnResult().responseHeaders)
	}


	fun createSoknadForSkjemanr(skjemanr: String, spraak: String = "nb_NO"): InnsendingApiResponse<DokumentSoknadDto> {
		val opprettSoknadBody = OpprettSoknadBody(skjemanr, spraak)
		val response = webTestClient.post()
			.uri("$baseUrl/frontend/v1/soknad")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagTokenXToken(), null).toSingleValueMap())
			})
			.bodyValue(opprettSoknadBody)
			.exchange()
		return InnsendingApiResponse(response.returnResult().status, readBody(response,DokumentSoknadDto::class.java), response.returnResult().responseHeaders)
	}


	fun createSoknadRedirect(
		skjemaDto: SkjemaDto,
		forceCreate: Boolean = true
	): ResponseEntity<BodyStatusResponseDto> {
		val response = webTestClient.post()
			.uri { uriBuilder ->
				uriBuilder
					.path("/fyllUt/v1/soknad")
					.queryParam("force", forceCreate)
					.build()
				}
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagTokenXToken(), null).toSingleValueMap())
			})
			.bodyValue(skjemaDto)
			.exchange()

		val result = response.returnResult<BodyStatusResponseDto>()
		return ResponseEntity(result.responseBody.blockFirst(), result.responseHeaders, result.status)
	}


	fun utfyltSoknad(innsendingsId: String, skjemaDto: SkjemaDto): ResponseEntity<Unit> {
		val response = webTestClient.put()
			.uri("/fyllUt/v1/utfyltSoknad/${innsendingsId}")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagTokenXToken(), null).toSingleValueMap())
			})
			.bodyValue(skjemaDto)
			.exchange()
		val result = response.returnResult<Unit>()
		return ResponseEntity(result.responseBody.blockFirst(), result.responseHeaders, result.status)
	}


	fun updateSoknad(innsendingsId: String, skjemaDto: SkjemaDto): ResponseEntity<SkjemaDto>? {
		val response = webTestClient.put()
			.uri ("/fyllUt/v1/soknad/${innsendingsId}")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagTokenXToken(), null).toSingleValueMap())
			})
			.bodyValue(skjemaDto)
			.exchange()

		val result = response.returnResult<SkjemaDto>()
		return ResponseEntity(result.responseBody.blockFirst(), result.responseHeaders, result.status)
	}


	fun updateSoknadFail(innsendingsId: String, skjemaDto: SkjemaDto): ResponseEntity<RestErrorResponseDto>? {
		val response = webTestClient.put()
			.uri ("/fyllUt/v1/soknad/${innsendingsId}")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagTokenXToken(), null).toSingleValueMap())
			})
			.bodyValue(skjemaDto)
			.exchange()

		val result = response.returnResult<RestErrorResponseDto>()
		return ResponseEntity(result.responseBody.blockFirst(), result.responseHeaders, result.status)
	}


	fun deleteSoknad(innsendingsId: String): InnsendingApiResponse<BodyStatusResponseDto>? {
		val response = webTestClient.delete()
			.uri ("http://localhost:${serverPort}/fyllUt/v1/soknad/${innsendingsId}")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagTokenXToken(), null).toSingleValueMap())
			})
			.exchange()

		val result = response.returnResult<RestErrorResponseDto>()
		return InnsendingApiResponse(response.returnResult().status, readBody(response,BodyStatusResponseDto::class.java), response.returnResult().responseHeaders)

	}


	fun getPrefillData(properties: String): ResponseEntity<PrefillData>? {
		val response = webTestClient.get()
			.uri("${baseUrl}/fyllUt/v1/prefill-data?properties=$properties")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagTokenXToken(), null).toSingleValueMap())
			})
			.exchange()

		val result = response.returnResult<PrefillData>()
		return ResponseEntity(result.responseBody.blockFirst(), result.responseHeaders, result.status)
	}


	fun getPrefillDataFail(properties: String): ResponseEntity<RestErrorResponseDto>? {
		val response = webTestClient.get()
			.uri ("${baseUrl}/fyllUt/v1/prefill-data?properties=$properties")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagTokenXToken(), null).toSingleValueMap())
			})
			.exchange()

		val result = response.returnResult<RestErrorResponseDto>()
		return ResponseEntity(result.responseBody.blockFirst(), result.responseHeaders, result.status)
	}

	fun getSoknadSendinn(innsendingsId: String): InnsendingApiResponse<DokumentSoknadDto> {
		val response = webTestClient.get()
			.uri("http://localhost:${serverPort}/frontend/v1/soknad/${innsendingsId}")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagTokenXToken(), null).toSingleValueMap())
			})
			.exchange()

		val body = readBody(response, DokumentSoknadDto::class.java)
		return InnsendingApiResponse(response.returnResult().status, body, response.returnResult().responseHeaders)
	}


	fun addVedlegg(innsendingsId: String, postVedleggDto: PostVedleggDto): InnsendingApiResponse<VedleggDto> {
		val response = webTestClient.post()
			.uri("${baseUrl}/frontend/v1/soknad/${innsendingsId}/vedlegg")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagTokenXToken(), null).toSingleValueMap())
			})
			.bodyValue(postVedleggDto)
			.exchange()

		val body = readBody(response, VedleggDto::class.java)
		return InnsendingApiResponse(response.returnResult().status, body, response.returnResult().responseHeaders)
	}


	fun patchVedlegg(innsendingsId: String, vedleggsId: Long, patchVedleggDto: PatchVedleggDto): InnsendingApiResponse<VedleggDto> {
		val response = webTestClient.patch()
			.uri("${baseUrl}/frontend/v1/soknad/${innsendingsId}/vedlegg/${vedleggsId}")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagTokenXToken(), null).toSingleValueMap())
			})
			.bodyValue(patchVedleggDto)
			.exchange()

		val body = readBody(response, VedleggDto::class.java)
		return InnsendingApiResponse(response.returnResult().status, body, response.returnResult().responseHeaders)
	}

	fun uploadFile(
		innsendingsId: String,
		vedleggsId: Long,
		file: ByteArray = Hjelpemetoder.getBytesFromFile("/litenPdf.pdf")
	): InnsendingApiResponse<FilDto> {
		val token: String = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val builder = MultipartBodyBuilder()
		builder.part("file", file)
			.filename("litenPdf.pdf")
			.contentType(MediaType.APPLICATION_PDF)

		val response = webTestClient.post()
			.uri("$baseUrl/frontend/v1/soknad/$innsendingsId/vedlegg/$vedleggsId/fil")
			.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.bodyValue(builder.build())
			.exchange()

		val body = readBody(response, FilDto::class.java)
		return InnsendingApiResponse(response.returnResult().status, body)
	}


	private fun uploadFile(
		innsendingId: String,
		vedleggId: String,
		filePath: String = "/litenPdf.pdf",
		authToken: String? = null,
		applicationPath: String,
	) : InnsendingApiResponse<FileDto> {
		val file = Hjelpemetoder.getBytesFromFile(filePath)
		val builder = MultipartBodyBuilder()
		builder.part("file", file)
			.filename("litenPdf.pdf")
			.contentType(MediaType.APPLICATION_PDF)

		val response = webTestClient.post()
			.uri("$baseUrl/v1/$applicationPath/$innsendingId/attachments/$vedleggId")
			.header(HttpHeaders.AUTHORIZATION, "Bearer $authToken")
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.bodyValue(builder.build())
			.exchange()

		val body = readBody(response, FileDto::class.java)
		return InnsendingApiResponse(response.returnResult().status, body, response.returnResult().responseHeaders)
	}

	fun uploadAttachmentFile(
		innsendingId: String,
		vedleggId: String,
		filePath: String = "/litenPdf.pdf",
		authToken: String? = null,
	): InnsendingApiResponse<FileDto> {
		val token: String = authToken ?: TokenGenerator(mockOAuth2Server).lagTokenXToken()
		return uploadFile(
			innsendingId = innsendingId,
			vedleggId = vedleggId,
			filePath = filePath,
			authToken = token,
			applicationPath = "application-digital"
		)
	}

	fun createEttersending(opprettEttersending: OpprettEttersending, envQualifier: EnvQualifier? = null): InnsendingApiResponse<DokumentSoknadDto> {
		val headers: Map<String, String>? = if (envQualifier != null) mapOf(
			"Nav-Env-Qualifier" to envQualifier.value
		) else null
		val response = webTestClient.post()
			.uri("${baseUrl}/fyllut/v1/ettersending")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagTokenXToken(), headers).toSingleValueMap())
			})
			.bodyValue(opprettEttersending)
			.exchange()

		val body = readBody(response, DokumentSoknadDto::class.java)
		return InnsendingApiResponse(response.returnResult().status, body, response.returnResult().responseHeaders)
	}


	fun createEttersendingsOppgave(opprettEttersendingsOppgave: EksternEttersendingsOppgave): ResponseEntity<DokumentSoknadDto> {
		val response = webTestClient.post()
			.uri("${baseUrl}/ekstern/v1/oppgaver")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagAzureOBOToken(), null).toSingleValueMap())
			})
			.bodyValue(opprettEttersendingsOppgave)
			.exchange()

		val body = readBody(response, DokumentSoknadDto::class.java)
		return ResponseEntity(body.first, response.returnResult().responseHeaders, response.returnResult().status)
	}


	fun oppgaveHentSoknaderForSkjemanr(
		skjemanr: String,
		brukerId: String,
		soknadstyper: List<SoknadType>?,
		navCallId: String?
	): ResponseEntity<List<DokumentSoknadDto>> {

		val response = webTestClient.method(HttpMethod.GET) // <-- Endringen er her
			.uri("${baseUrl}/ekstern/v1/oppgaver")
			.headers { httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagAzureOBOToken(), null).toSingleValueMap())
			}
			.bodyValue(BrukerSoknadRequest(brukerId = brukerId, skjemanr = skjemanr, soknadstyper = soknadstyper))
			.exchange()

		val body = parseListResponse(response, DokumentSoknadDto::class.java)
		return ResponseEntity(body.first, response.returnResult().responseHeaders, response.returnResult().status)
	}

	fun eksternOppgaveSlett(innsendingsId: String): ResponseEntity<BodyStatusResponseDto> {
		val response = webTestClient.delete()
			.uri("${baseUrl}/ekstern/v1/oppgaver/${innsendingsId}")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagAzureOBOToken(), null).toSingleValueMap())
			})
			.exchange()

		val body = readBody(response, BodyStatusResponseDto::class.java)
		return ResponseEntity(body.first, response.returnResult().responseHeaders, response.returnResult().status)
	}

	fun eksternOppgaveSlettFail(innsendingsId: String): ResponseEntity<RestErrorResponseDto> {
		val response = webTestClient.delete()
			.uri("${baseUrl}/ekstern/v1/oppgaver/${innsendingsId}")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagAzureOBOToken(), null).toSingleValueMap())
			})
			.exchange()

		val body = readBody(response, RestErrorResponseDto::class.java)
		return ResponseEntity(body.first, response.returnResult().responseHeaders, response.returnResult().status)

	}


	fun createEksternEttersending(eksternOpprettEttersending: EksternOpprettEttersending, envQualifier: EnvQualifier? = null): InnsendingApiResponse<DokumentSoknadDto> {
		val headers: Map<String, String>? = if (envQualifier != null) mapOf(
			"Nav-Env-Qualifier" to envQualifier.value
		) else null
		val response = webTestClient.post()
			.uri("${baseUrl}/ekstern/v1/ettersending")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagTokenXToken(), headers).toSingleValueMap())
			})
			.bodyValue(eksternOpprettEttersending)
			.exchange()

		val body = readBody(response, DokumentSoknadDto::class.java)
		return InnsendingApiResponse(response.returnResult().status, body, response.returnResult().responseHeaders)
	}

	fun deleteEksternEttersending(innsendingsId: String): ResponseEntity<BodyStatusResponseDto> {
		val response = webTestClient.delete()
			.uri("${baseUrl}/ekstern/v1/ettersending/${innsendingsId}")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagTokenXToken(), null).toSingleValueMap())
			})
			.exchange()

		val body = readBody(response, BodyStatusResponseDto::class.java)
		return ResponseEntity(body.first, response.returnResult().responseHeaders, response.returnResult().status)

	}

	fun deleteEksternEttersendingFail(innsendingsId: String): ResponseEntity<RestErrorResponseDto> {
		val response = webTestClient.delete()
			.uri("${baseUrl}/ekstern/v1/ettersending/${innsendingsId}")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagTokenXToken(), null).toSingleValueMap())
			})
			.exchange()

		val body = readBody(response, RestErrorResponseDto::class.java)
		return ResponseEntity(body.second, response.returnResult().responseHeaders, response.returnResult().status)

	}


	@Deprecated("Replace with uploadNologinFileV2")
	fun uploadNologinFile(
		innsendingId: String? = null,
		vedleggId: String,
		filePath: String = "/litenPdf.pdf",
		authToken: String? = null,
	): InnsendingApiResponse<LastOppFilResponse> {
		val token: String = authToken ?: TokenGenerator(mockOAuth2Server).lagAzureM2MToken(listOf("nologin-access"))
		val file = Hjelpemetoder.getBytesFromFile(filePath)

		val builder = MultipartBodyBuilder()
		builder.part("filinnhold", file)
			.filename("litenPdf.pdf")
			.contentType(MediaType.APPLICATION_PDF)
		builder.part("vedleggId", vedleggId)
		if (innsendingId != null) {builder.part("innsendingId", innsendingId)}

		val response = webTestClient.post()
			.uri("${baseUrl}/v1/nologin-fillager")
			.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.bodyValue(builder.build())
			.exchange()

		val body = readBody(response, LastOppFilResponse::class.java)
		return InnsendingApiResponse(response.returnResult().status, body, response.returnResult().responseHeaders)
	}


	@Deprecated("Is replaced by submitNologinApplication")
	fun sendInnNologinSoknad(skjemaDto: SkjemaDtoV2): InnsendingApiResponse<KvitteringsDto> {
		val token = TokenGenerator(mockOAuth2Server).lagAzureM2MToken(listOf("nologin-access"))
		val response = webTestClient.post()
			.uri("${baseUrl}/v1/nologin-soknad")
			.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
			.bodyValue(skjemaDto)
			.exchange()

		val body = readBody(response, KvitteringsDto::class.java)
		return InnsendingApiResponse(response.returnResult().status, body, response.returnResult().responseHeaders)
	}


	fun submitNologinApplication(
		innsendingsId: String,
		formNumber: String = "NAV 11-12.15B",
		title: String = "Søknad om testing",
		tema: String = "BIL",
		brukerId: String? = TokenGenerator.subject,
		attachments: List<AttachmentDto>? = null,
		language: String = "nb",
		mainDocumentPath: String = "/litenPdf.pdf",
		mainDocumentAltPath: String = "/__files/barnepass-NAV-11-12.15B.json",
		authToken: String? = null,
		avsender: AvsenderDto? = null
	): InnsendingApiResponse<ApplicationSubmissionResponse> {
		val token: String = authToken ?: TokenGenerator(mockOAuth2Server).lagAzureM2MToken(listOf("nologin-access"))
		val headers = Hjelpemetoder.createHeaders(token, MediaType.APPLICATION_JSON)
		val mainDocumentByteArray: ByteArray =  Hjelpemetoder.getBytesFromFile(mainDocumentPath)
		val mainDocumentAltByteArray: ByteArray =  Hjelpemetoder.getBytesFromFile(mainDocumentAltPath)

		val request = SubmitApplicationRequest(
			formNumber = formNumber,
			title = title,
			tema = tema,
			language = language,
			mainDocument = mainDocumentByteArray,
			mainDocumentAlt = mainDocumentAltByteArray,
			attachments = attachments,
			bruker = brukerId,
			avsender = avsender,
		)
		val httpEntity = HttpEntity(request, headers)

		val response = webTestClient.post()
			.uri("${baseUrl}/v1/application-nologin/${innsendingsId}")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(token = token).toSingleValueMap())
			})
			.bodyValue(request)
			.exchange()

		val body = readBody(response, ApplicationSubmissionResponse::class.java)
		return InnsendingApiResponse(response.returnResult().status, body, response.returnResult().responseHeaders)
	}


	fun uploadNologinFileV2(
		innsendingId: String,
		vedleggId: String,
		filePath: String = "/litenPdf.pdf",
		authToken: String? = null,
	): InnsendingApiResponse<FileDto> {
		val token: String = authToken ?: TokenGenerator(mockOAuth2Server).lagAzureM2MToken(listOf("nologin-access"))
		return uploadFile(
			innsendingId = innsendingId,
			vedleggId = vedleggId,
			filePath = filePath,
			authToken = token,
			applicationPath = "application-nologin"
		)
	}

	fun submitDigitalApplication(
		soknad: SkjemaDto,
		attachments: List<AttachmentDto>? = null,
		language: String = "nb",
		mainDocumentPath: String = "/litenPdf.pdf",
		mainDocumentAltPath: String = "/__files/barnepass-NAV-11-12.15B.json",
		authToken: String? = null,
		bruker: String? = null,
		avsender: AvsenderDto? = null,
	): InnsendingApiResponse<ApplicationSubmissionResponse> {
		val token: String = authToken ?: TokenGenerator(mockOAuth2Server).lagTokenXToken()
		val headers = Hjelpemetoder.createHeaders(token, MediaType.APPLICATION_JSON)
		val mainDocumentByteArray: ByteArray =  Hjelpemetoder.getBytesFromFile(mainDocumentPath)
		val mainDocumentAltByteArray: ByteArray =  Hjelpemetoder.getBytesFromFile(mainDocumentAltPath)

		val request = SubmitApplicationRequest(
			formNumber = soknad.skjemanr,
			title = soknad.tittel,
			tema = soknad.tema,
			language = language,
			mainDocument = mainDocumentByteArray,
			mainDocumentAlt = mainDocumentAltByteArray,
			attachments = attachments,
			bruker = bruker ?: soknad.brukerId,
			avsender = avsender,
		)
		val httpEntity = HttpEntity(request, headers)

		val response = webTestClient.post()
			.uri("${baseUrl}/v1/application-digital/${soknad.innsendingsId!!}")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(token = token).toSingleValueMap())
			})
			.bodyValue(request)
			.exchange()

		val body = readBody(response, ApplicationSubmissionResponse::class.java)
		return InnsendingApiResponse(response.returnResult().status, body, response.returnResult().responseHeaders)
	}


	fun hentInnsendteFiler(innsendingsId: String, uuids: List<String>): InnsendingApiResponse<List<SoknadFile>> {
		val authToken = TokenGenerator(mockOAuth2Server).lagAzureM2MToken()
		val response = webTestClient.get()
			.uri("${baseUrl}/innsendte/v1/files/${uuids.joinToString(",")}")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(token = authToken, mapOf("x-innsendingId" to innsendingsId)).toSingleValueMap())
			})
			.exchange()

		val body = parseListResponse(response, SoknadFile::class.java)
		return InnsendingApiResponse(response.returnResult().status, body, response.returnResult().responseHeaders)
	}


	fun sendInnSoknad(innsendingsId: String, envQualifier: EnvQualifier? = null): InnsendingApiResponse<KvitteringsDto> {
		val headers: Map<String, String>? = if (envQualifier != null) mapOf(
			"Nav-Env-Qualifier" to envQualifier.value
		) else null
		val response = webTestClient.post()
			.uri("${baseUrl}/frontend/v1/sendInn/${innsendingsId}")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagTokenXToken(), headers).toSingleValueMap())
			})
			.exchange()

		val body = readBody(response, KvitteringsDto::class.java)
		return InnsendingApiResponse(response.returnResult().status, body, response.returnResult().responseHeaders)
	}


	private fun <T> parseListResponse(
		response: WebTestClient.ResponseSpec,
		clazz: Class<T>
	): Pair<List<T>?, RestErrorResponseDto?> =
		when {
			response.returnResult().status.is2xxSuccessful -> Pair(
				objectMapper.readValue(
					response.returnResult().responseBodyContent,
					objectMapper.typeFactory.constructCollectionType(List::class.java, clazz)
				), null
			)

			else -> Pair(null, objectMapper.readValue(response.returnResult().responseBodyContent, RestErrorResponseDto::class.java))
		}


	fun getSoknad(innsendingsId: String): ResponseEntity<SkjemaDto>? {
		val response = webTestClient.get()
			.uri("${baseUrl}/fyllUt/v1/soknad/${innsendingsId}")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagTokenXToken(), null).toSingleValueMap())
			})
			.exchange()

		val body = readBody(response, SkjemaDto::class.java)
		return ResponseEntity(body.first, response.returnResult().responseHeaders, response.returnResult().status)

	}

	// Query param ex: "soknad,ettersendelse"
	fun getExistingSoknader(skjemanr: String, queryParam: String? = null): ResponseEntity<List<DokumentSoknadDto>>? {
		val url = if (queryParam != null) {
			"http://localhost:${serverPort}/frontend/v1/skjema/${skjemanr}/soknader?soknadstyper=$queryParam"
		} else {
			"http://localhost:${serverPort}/frontend/v1/skjema/${skjemanr}/soknader"
		}

		val responseType = object : ParameterizedTypeReference<List<DokumentSoknadDto>>() {}
		val response = webTestClient.get()
			.uri(url)
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagTokenXToken(), null).toSingleValueMap())
			})
			.exchange()
		val body = parseListResponse(response, DokumentSoknadDto::class.java)
		return ResponseEntity(body.first, response.returnResult().responseHeaders, response.returnResult().status)

	}

	fun getSoknaderForSkjemanr(
		skjemanr: String,
		soknadstyper: List<SoknadType>? = emptyList()
	): ResponseEntity<List<DokumentSoknadDto>> {
		var query = ""
		if (soknadstyper?.isNotEmpty() == true) {
			query = "?soknadstyper=${soknadstyper.joinToString()}"
		}
		val response = webTestClient.get()
			.uri("${baseUrl}/ekstern/v1/skjema/${skjemanr}/soknader${query}")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagTokenXToken(), null).toSingleValueMap())
			})
			.exchange()

		val body = parseListResponse(response, DokumentSoknadDto::class.java)
		return ResponseEntity(body.first, response.returnResult().responseHeaders, response.returnResult().status)
	}


	fun getAktiviteter(aktivitetEndepunkt: AktivitetEndepunkt): ResponseEntity<List<Aktivitet>>? {
		val dagligReise = if (aktivitetEndepunkt == AktivitetEndepunkt.dagligreise) "true" else "false"
		val response = webTestClient.get()
			.uri("${baseUrl}/fyllUt/v1/aktiviteter?dagligreise=${dagligReise}")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagTokenXToken(), null).toSingleValueMap())
			})
			.exchange()

		val body = parseListResponse(response, Aktivitet::class.java)
		return ResponseEntity(body.first, response.returnResult().responseHeaders, response.returnResult().status)
	}


	fun runAdminJob(jobName: String, authToken: String? = null): ResponseEntity<Unit> {
		val token = authToken ?: TokenGenerator(mockOAuth2Server).lagAzureOBOToken(scopes = "admin-access", navIdent = "Z123456")
		val response = webTestClient.post()
			.uri("${baseUrl}/admin/v1/job")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(token, null).toSingleValueMap())
			})
			.bodyValue(RunJobRequest(jobName))
			.exchange()
		return ResponseEntity(Unit, response.returnResult().responseHeaders, response.returnResult().status)
	}


	fun createLospost(
		opprettLospost: OpprettLospost,
		envQualifier: EnvQualifier? = null
	): InnsendingApiResponse<LospostDto> {
		val headers: Map<String, String>? = if (envQualifier != null) mapOf(
			"Nav-Env-Qualifier" to envQualifier.value
		) else null
		val response = webTestClient.post()
			.uri("${baseUrl}/fyllut/v1/lospost")
			.headers({ httpHeaders ->
				httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagTokenXToken(), headers).toSingleValueMap())
			})
			.bodyValue(opprettLospost)
			.exchange()

		val body = readBody(response, LospostDto::class.java)
		return InnsendingApiResponse(response.returnResult().status, body, response.returnResult().responseHeaders)
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




	/*

		fun getSoknad(innsendingsId: String): ResponseEntity<SkjemaDto>? {
			val response = webTestClient.put()
				.uri ("/fyllUt/v1/soknad/${innsendingsId}")
				.headers({ httpHeaders ->
					httpHeaders.setAll(Hjelpemetoder.createHeaders(TokenGenerator(mockOAuth2Server).lagTokenXToken(), null).toSingleValueMap())
				})
				.bodyValue(skjemaDto)
				.exchange()

			val result = response.returnResult<RestErrorResponseDto>()
			return ResponseEntity(result.responseBody.blockFirst(), result.responseHeaders, result.status)


			return restTemplate.exchange(
				"${baseUrl}/fyllUt/v1/soknad/${innsendingsId}",
				HttpMethod.GET,
				createHttpEntity(null),
				SkjemaDto::class.java
			)
		}

	*/

}
