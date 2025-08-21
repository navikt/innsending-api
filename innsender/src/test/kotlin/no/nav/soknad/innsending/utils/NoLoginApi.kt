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
import org.springframework.http.*
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.util.UriComponentsBuilder
import kotlin.test.assertEquals


class NoLoginApi(val restTemplate: TestRestTemplate, val serverPort: Int, val mockOAuth2Server: MockOAuth2Server) {

	val baseUrl = "http://localhost:${serverPort}"
	val objectMapper: ObjectMapper = jacksonObjectMapper().findAndRegisterModules()

	private fun <T> createHttpEntity(body: T, map: Map<String, String>? = mapOf()): HttpEntity<T> {
		val token: String = TokenGenerator(mockOAuth2Server).lagAzureToken()
		return HttpEntity(body, Hjelpemetoder.createHeaders(token, map))
	}

	private fun <T> createHttpEntity(body: T, map: Map<String, String>? = mapOf(), issuer: String): HttpEntity<T> {
		if (issuer != AZURE)	return createHttpEntity(body, map)
		val token: String = TokenGenerator(mockOAuth2Server).lagAzureToken()
		return HttpEntity(body, Hjelpemetoder.createHeaders(token, map))
	}

	fun createAndSendInSoknad(dokumentDto: SkjemaDtoV2, envQualifier: EnvQualifier? = null): InnsendingApiResponse<KvitteringsDto> {
		val headers: Map<String, String>? = if (envQualifier != null) mapOf(
			"Nav-Env-Qualifier" to envQualifier.value
		) else null
		val uri = UriComponentsBuilder.fromHttpUrl("${baseUrl}/v1/nologin-soknad")
			.build()
			.toUri()

		val body = dokumentDto

		val response = restTemplate.exchange(uri, HttpMethod.POST, createHttpEntity(body, headers), String::class.java)

		val responseBody = readBody(response, KvitteringsDto::class.java)
		return InnsendingApiResponse(response.statusCode, responseBody, response.headers)
	}


	fun uploadFile(
		innsendingsId: String,
		vedleggsId: Long,
		file: ByteArray = Hjelpemetoder.getBytesFromFile("/litenPdf.pdf")
	): InnsendingApiResponse<LastOppFilResponse> {
		val token: String = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val requestBody: MultiValueMap<String, ByteArray> = LinkedMultiValueMap()
		requestBody.add("file", file)
		requestBody.add("vedleggsId", vedleggsId.toString().toByteArray())
		requestBody.add("innsendingsId", innsendingsId.toByteArray())

		val httpEntity = HttpEntity(requestBody, Hjelpemetoder.createHeaders(token, MediaType.MULTIPART_FORM_DATA))


			val response = restTemplate.exchange(
			"${baseUrl}/v1/nologin-fillager",
			HttpMethod.POST,
			httpEntity,
			String::class.java
		)

		val responseBody = readBody(response, LastOppFilResponse::class.java)
		return InnsendingApiResponse(response.statusCode, responseBody)
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
