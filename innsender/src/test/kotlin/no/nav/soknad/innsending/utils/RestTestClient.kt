package no.nav.soknad.innsending.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.web.reactive.server.WebTestClient

class RestTestClient(private val client_: WebTestClient) {
		private val client = client_.mutate().responseTimeout(java.time.Duration.ofMinutes(3L)).build()
    private val objectMapper: ObjectMapper = jacksonObjectMapper().findAndRegisterModules()

    fun <T : Any> exchange(url: String, method: HttpMethod, entity: org.springframework.http.HttpEntity<*>, responseType: Class<T>): ResponseEntity<T> {
        var requestSpec: WebTestClient.RequestHeadersSpec<*> = when (method) {
            HttpMethod.GET -> client.get().uri(url)
            HttpMethod.POST -> client.post().uri(url)
            HttpMethod.PUT -> client.put().uri(url)
            HttpMethod.DELETE -> client.delete().uri(url)
            HttpMethod.PATCH -> client.patch().uri(url)
            else -> throw IllegalArgumentException("Unsupported HTTP method: $method")
        }

        entity.headers.forEach { key, values ->
            values.forEach { value ->
                requestSpec.header(key, value)
            }
        }

        if (entity.body != null) {
					entity.body?.let { (requestSpec as? WebTestClient.RequestBodySpec)?.bodyValue(it) }
        }

        val result = requestSpec.exchange().returnResult(String::class.java)
        val bodyList = result.responseBody.collectList().block() ?: emptyList()
        val bodyString = bodyList.joinToString("")
        val responseBody = if (bodyString.isNotEmpty()) objectMapper.readValue(bodyString, responseType) else null
        return ResponseEntity(responseBody, result.responseHeaders, HttpStatus.valueOf(result.status.value()))
    }

    fun <T : Any> exchange(url: java.net.URI, method: HttpMethod, entity: org.springframework.http.HttpEntity<*>, responseType: Class<T>): ResponseEntity<T> {
        return exchange(url.toString(), method, entity, responseType)
    }

    fun <T : Any> exchange(url: String, method: HttpMethod, entity: org.springframework.http.HttpEntity<*>, responseType: ParameterizedTypeReference<T>): ResponseEntity<T> {
        var requestSpec: WebTestClient.RequestHeadersSpec<*> = when (method) {
            HttpMethod.GET -> client.get().uri(url)
            HttpMethod.POST -> client.post().uri(url)
            HttpMethod.PUT -> client.put().uri(url)
            HttpMethod.DELETE -> client.delete().uri(url)
            HttpMethod.PATCH -> client.patch().uri(url)
            else -> throw IllegalArgumentException("Unsupported HTTP method: $method")
        }

        entity.headers.forEach { key, values ->
            values.forEach { value ->
                requestSpec.header(key, value)
            }
        }

        if (entity.body != null) {
					entity.body?.let { (requestSpec as? WebTestClient.RequestBodySpec)?.bodyValue(it) }
        }

        val result = requestSpec.exchange().returnResult(String::class.java)
        val bodyList = result.responseBody.collectList().block() ?: emptyList()
        val bodyString = bodyList.joinToString("")
        val responseBody = if (bodyString.isNotEmpty()) {
            val javaType = objectMapper.typeFactory.constructType(responseType.type)
            objectMapper.readValue(bodyString, javaType) as T
        } else null
        return ResponseEntity(responseBody, result.responseHeaders, HttpStatus.valueOf(result.status.value()))
    }

    fun <T : Any> exchange(url: java.net.URI, method: HttpMethod, entity: org.springframework.http.HttpEntity<*>, responseType: ParameterizedTypeReference<T>): ResponseEntity<T> {
        return exchange(url.toString(), method, entity, responseType)
    }
}
