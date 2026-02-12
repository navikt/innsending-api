package no.nav.soknad.innsending.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.service.config.ConfigDefinition
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

	private fun <T> createHttpEntity(body: T, map: Map<String, String>? = mapOf(), authToken: String? = null): HttpEntity<T> {
		val token: String = authToken ?: TokenGenerator(mockOAuth2Server).lagTokenXToken()
		return HttpEntity(body, Hjelpemetoder.createHeaders(token, map))
	}

	private fun <T> createHttpEntity(body: T, map: Map<String, String>? = mapOf(), issuer: String, authToken: String? = null): HttpEntity<T> {
		if (issuer != AZURE) return createHttpEntity(body, map)
		val token: String = authToken ?: TokenGenerator(mockOAuth2Server).lagAzureToken()
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

	fun createSoknadForSkjemanr(skjemanr: String, spraak: String = "nb_NO"): InnsendingApiResponse<DokumentSoknadDto> {
		val opprettSoknadBody = OpprettSoknadBody(skjemanr, spraak)
		val response = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad",
			HttpMethod.POST,
			createHttpEntity(opprettSoknadBody),
			String::class.java
		)
		val body = readBody(response, DokumentSoknadDto::class.java)
		return InnsendingApiResponse(response.statusCode, body, response.headers)
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

		val headers = Hjelpemetoder.createHeaders(token, MediaType.MULTIPART_FORM_DATA)

		// Per-part headers: content-disposition with filename
		val partHeaders = HttpHeaders()
		partHeaders.contentType = MediaType.APPLICATION_PDF
		partHeaders.setContentDispositionFormData("file", "litenPdf.pdf")

		val filePart: HttpEntity<ByteArray> = HttpEntity(file, partHeaders)

		val requestBody: MultiValueMap<String, Any> = LinkedMultiValueMap()
		requestBody.add("file", filePart)

		val httpEntity = HttpEntity(requestBody, headers)

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

    fun getConfig(config: ConfigDefinition, authToken: String? = null): InnsendingApiResponse<ConfigValueDto> {
			val token = authToken ?: TokenGenerator(mockOAuth2Server).lagAzureOBOToken(scopes = "config-admin-access", navIdent = "Z123456")
				val response = restTemplate.exchange(
						"${baseUrl}/v1/config/${config.key}",
						HttpMethod.GET,
						createHttpEntity(null, null, AZURE, token),
						String::class.java
				)
				val body = readBody(response, ConfigValueDto::class.java)
				return InnsendingApiResponse(response.statusCode, body, response.headers)
		}

	fun setConfig(config: ConfigDefinition, string: String?, authToken: String? = null): InnsendingApiResponse<ConfigValueDto> {
		val token = authToken ?: TokenGenerator(mockOAuth2Server).lagAzureOBOToken(scopes = "config-admin-access", navIdent = "Z123456")
		val response = restTemplate.exchange(
				"${baseUrl}/v1/config/${config.key}",
				HttpMethod.PUT,
				createHttpEntity(SetConfigRequest(string), null, AZURE, token),
				String::class.java
		)
		val body = readBody(response, ConfigValueDto::class.java)
		return InnsendingApiResponse(response.statusCode, body, response.headers)
	}

	@Deprecated("Replace with uploadNologinFileV2")
	fun uploadNologinFile(
		innsendingId: String? = null,
		vedleggId: String,
		filePath: String = "/litenPdf.pdf",
		authToken: String? = null,
	): InnsendingApiResponse<LastOppFilResponse> {
		val token: String = authToken ?: TokenGenerator(mockOAuth2Server).lagAzureM2MToken(listOf("nologin-access"))
		val headers = Hjelpemetoder.createHeaders(token, MediaType.MULTIPART_FORM_DATA)

		val partHeaders = HttpHeaders()
		partHeaders.contentType = MediaType.APPLICATION_PDF
		partHeaders.setContentDispositionFormData("filinnhold", filePath.replace("/", ""))
		val fileByteArray: ByteArray =  Hjelpemetoder.getBytesFromFile(filePath)
		val filePart: HttpEntity<ByteArray> = HttpEntity(fileByteArray, partHeaders)

		val requestBody: MultiValueMap<String, Any> = LinkedMultiValueMap()
		requestBody.add("filinnhold", filePart)
		requestBody.add("vedleggId", vedleggId)
		innsendingId?.let {
			requestBody.add("innsendingId", it)
		}

		val httpEntity = HttpEntity(requestBody, headers)

		val response = restTemplate.exchange(
			"${baseUrl}/v1/nologin-fillager",
			HttpMethod.POST,
			httpEntity,
			String::class.java
		)

		val body = readBody(response, LastOppFilResponse::class.java)
		return InnsendingApiResponse(response.statusCode, body)
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

	private fun uploadFile(
		innsendingId: String,
		vedleggId: String,
		filePath: String = "/litenPdf.pdf",
		authToken: String? = null,
		applicationPath: String,
	) : InnsendingApiResponse<FileDto> {
		val headers = Hjelpemetoder.createHeaders(authToken, MediaType.MULTIPART_FORM_DATA)

		val partHeaders = HttpHeaders()
		partHeaders.contentType = MediaType.APPLICATION_PDF
		partHeaders.setContentDispositionFormData("file", filePath.replace("/", ""))
		val fileByteArray: ByteArray =  Hjelpemetoder.getBytesFromFile(filePath)
		val filePart: HttpEntity<ByteArray> = HttpEntity(fileByteArray, partHeaders)

		val requestBody: MultiValueMap<String, Any> = LinkedMultiValueMap()
		requestBody.add("file", filePart)

		val httpEntity = HttpEntity(requestBody, headers)

		val response = restTemplate.exchange(
			"${baseUrl}/v1/$applicationPath/$innsendingId/attachments/$vedleggId",
			HttpMethod.POST,
			httpEntity,
			String::class.java
		)

		val body = readBody(response, FileDto::class.java)
		return InnsendingApiResponse(response.statusCode, body)
	}

	fun submitDigitalApplication(
		soknad: SkjemaDto,
		attachments: List<AttachmentDto>? = null,
		language: String = "nb",
		mainDocumentPath: String = "/litenPdf.pdf",
		mainDocumentAltPath: String = "/__files/barnepass-NAV-11-12.15B.json",
		authToken: String? = null,
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
			bruker = soknad.brukerId,
			avsender = AvsenderDto(navn = "Test Navn"),
		)
		val httpEntity = HttpEntity(request, headers)

		val response = restTemplate.exchange(
			"${baseUrl}/v1/application-digital/${soknad.innsendingsId!!}",
			HttpMethod.POST,
			httpEntity,
			String::class.java
		)

		val body = readBody(response, ApplicationSubmissionResponse::class.java)
		return InnsendingApiResponse(response.statusCode, body, response.headers)
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

		val response = restTemplate.exchange(
			"${baseUrl}/v1/application-nologin/${innsendingsId}",
			HttpMethod.POST,
			httpEntity,
			String::class.java
		)

		val body = readBody(response, ApplicationSubmissionResponse::class.java)
		return InnsendingApiResponse(response.statusCode, body, response.headers)
	}

	@Deprecated("Is replaced by submitNologinApplication")
	fun sendInnNologinSoknad(skjemaDto: SkjemaDtoV2): InnsendingApiResponse<KvitteringsDto> {
		val token = TokenGenerator(mockOAuth2Server).lagAzureM2MToken(listOf("nologin-access"))
		val response = restTemplate.exchange(
			"${baseUrl}/v1/nologin-soknad",
			HttpMethod.POST,
			createHttpEntity(skjemaDto, authToken = token),
			String::class.java
		)

		val body = readBody(response, KvitteringsDto::class.java)
		return InnsendingApiResponse(response.statusCode, body, response.headers)
	}

	fun hentInnsendteFiler(innsendingsId: String, uuids: List<String>): InnsendingApiResponse<List<SoknadFile>> {
		val authToken = TokenGenerator(mockOAuth2Server).lagAzureM2MToken()
		val response = restTemplate.exchange(
			"${baseUrl}/innsendte/v1/files/${uuids.joinToString(",")}",
			HttpMethod.GET,
			createHttpEntity(null, mapOf("x-innsendingId" to innsendingsId), authToken = authToken),
			String::class.java
		)

		val body = parseListResponse(response, SoknadFile::class.java)
		return InnsendingApiResponse(response.statusCode, body, response.headers)
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

	private fun <T> parseListResponse(
		response: ResponseEntity<String>,
		clazz: Class<T>
	): Pair<List<T>?, RestErrorResponseDto?> =
		when {
			response.statusCode.is2xxSuccessful -> Pair(
				objectMapper.readValue(
					response.body,
					objectMapper.typeFactory.constructCollectionType(List::class.java, clazz)
				), null
			)

			else -> Pair(null, objectMapper.readValue(response.body, RestErrorResponseDto::class.java))
		}

	private fun <T> parseSingleResponse(response: ResponseEntity<String>, clazz: Class<T>): Pair<T?, RestErrorResponseDto?> =
		when {
			response.statusCode.is2xxSuccessful -> Pair(objectMapper.readValue(response.body, clazz), null)
			else -> Pair(null, objectMapper.readValue(response.body, RestErrorResponseDto::class.java))
		}

}
