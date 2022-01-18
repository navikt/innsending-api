package no.nav.soknad.innsending.consumerapis.soknadsfillager

import no.nav.soknad.arkivering.soknadsfillager.dto.FilElementDto
import no.nav.soknad.innsending.config.RestConfig
import org.apache.tomcat.util.codec.binary.Base64
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpMethod
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import java.util.stream.Collectors

@Service
class FilLagerClient(private val restConfig: RestConfig,
										 @Qualifier("authClient") private val webClient: WebClient) {

	private val filesInOneRequestToFilestorage = 5
	private val logger = LoggerFactory.getLogger(javaClass)

	fun hentFiler(filer: List<FilElementDto>): List<FilElementDto> {
		val fileIds = filer.stream().map {e -> e.uuid}.collect(Collectors.toList())
		try {

			logger.info("Getting files with ids: '$fileIds'")

			val files = getFiles(fileIds)

			logger.info("Received ${files.size} files with a sum of ${files.sumOf { it.fil?.size ?: 0 }} bytes")
			return files

		} catch (e: Exception) {
			if (e.cause is FileDeletedException) {
				logger.warn("Fetching of previously stored file(s): ${fileIds.joinToString{it}} failed because one or more are deleted in the file storage: ${e.message}")
				throw e
			} else {
				logger.error("Error retrieving files from the file storage", e)
				throw e
			}
		}
	}

	fun slettFiler(filer: List<FilElementDto>) {

		val fileIds = filer.stream().map {e -> e.uuid}.collect(Collectors.toList()).joinToString(",")
		try {

			logger.info("Calling file storage to delete '$fileIds'")
			deleteFiles(fileIds)
			logger.info("The files: $fileIds are deleted")

		} catch (e: Exception) {
			logger.warn("Failed to delete files from file storage. Everything is saved to Joark correctly, " +
				"so this error will be ignored. Affected file ids: '$fileIds'", e)
		}
	}


	fun lagreFiler(filer: List<FilElementDto>) {

		val fileIds = filer.stream().map {e -> e.uuid}.collect(Collectors.toList()).joinToString(",")
		try {

			logger.info("Calling file storage to save '$fileIds'")
			saveFiles(filer)
			logger.info("The files: $fileIds are saved")

		} catch (e: Exception) {
			logger.error("Failed to save files '$fileIds' in file storage. ", e)
			throw e
		}
	}

	private fun getFiles(fileIds: List<String>): List<FilElementDto> {

		val idChunks = fileIds.chunked(filesInOneRequestToFilestorage).map { it.joinToString(",") }
		val files = idChunks
			.mapNotNull { performGetCall(it) }
			.flatten()

		if (fileIds.size != files.size) {
			val fetchedFiles = files.map { it.uuid }
			throw Exception("Was not able to fetch the files with these ids: ${fileIds.filter { !fetchedFiles.contains(it) }}")
		}
		return files

	}

	private fun performGetCall(fileIds: String): List<FilElementDto>? {
		val uri = restConfig.filestorageHost + restConfig.filestorageEndpoint + restConfig.filestorageParameters + fileIds
		val method = HttpMethod.GET
		val webClient = setupWebClient(uri, method)

		return webClient
			.retrieve()
			.onStatus(
				{ httpStatus -> httpStatus.is4xxClientError || httpStatus.is5xxServerError },
				{ response -> response.bodyToMono(String::class.java).map {
					if (response.statusCode() == HttpStatus.GONE) {
						FileDeletedException("Got ${response.statusCode()} when requesting $method $uri - response body: '$it'")
					} else {
						Exception("Got ${response.statusCode()} when requesting $method $uri - response body: '$it'")
					}
				}
				}
			)
			.bodyToFlux(FilElementDto::class.java)
			.collectList()
			.block()
	}

	private fun deleteFiles(fileIds: String) {
		val uri = restConfig.filestorageHost + restConfig.filestorageEndpoint + restConfig.filestorageParameters + fileIds
		val method = HttpMethod.DELETE
		val webClient = setupWebClient(uri, method)

		webClient
			.retrieve()
			.onStatus(
				{ httpStatus -> httpStatus.is4xxClientError || httpStatus.is5xxServerError },
				{ response -> response.bodyToMono(String::class.java).map { Exception("Got ${response.statusCode()} when requesting $method $uri - response body: '$it'") } })
			.bodyToMono(String::class.java)
			.block()
	}

	private fun saveFiles(files: List<FilElementDto>) {
		val uri = restConfig.filestorageHost + restConfig.filestorageEndpoint
		val method = HttpMethod.POST
		val webClient = setupWebClient(uri, method)

		webClient
			.body(BodyInserters.fromValue(files))
			.retrieve()
			.onStatus(
				{ httpStatus -> httpStatus.is4xxClientError || httpStatus.is5xxServerError },
				{ response -> response.bodyToMono(String::class.java).map {
						Exception("Got ${response.statusCode()} when requesting $method $uri - response body: '$it'")
				}
				})
	}

	private fun setupWebClient(uri: String, method: HttpMethod): WebClient.RequestBodySpec {
		val auth = "${restConfig.sharedUsername}:${restConfig.sharedPassword}"
		val encodedAuth: ByteArray = Base64.encodeBase64(auth.toByteArray())
		val authHeader = "Basic " + String(encodedAuth)

		return webClient
			.method(method)
			.uri(uri)
			.contentType(APPLICATION_JSON)
			.accept(APPLICATION_JSON)
			.header("Authorization", authHeader)
	}

}
