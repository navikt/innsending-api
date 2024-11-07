package no.nav.soknad.pdfutilities.gotenberg

import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.pdfutilities.DocxToPdfInterface
import no.nav.soknad.pdfutilities.FiltypeSjekker
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Service
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.io.File

@Service
@Profile("prod | dev | test")
class GotenbergConvertToPdf(
	@Qualifier("gotenbergClient")
	private val gotenbergClient: RestClient,
) : DocxToPdfInterface {
	companion object GotenbergConsts {
		private const val LIBRE_OFFICE_ROUTE = "/forms/libreoffice/convert"
		private const val GOTENBERG_TRACE_HEADER = "gotenberg-trace"
	}

	private val logger = LoggerFactory.getLogger(GotenbergConvertToPdf::class.java)

	override fun toPdf(
		fileName: String,
		fileContent: ByteArray,
	): ByteArray {
		val pageProperties: PageProperties = PageProperties.Builder().build()
		val multipartBody = MultipartBodyBuilder().run {
			part("files", ByteArrayMultipartFile(fileName, fileContent).resource)
			pageProperties.all().forEach{ part(it.key, it.value) }
			build()
		}

		return convertFileRequest(fileName, multipartBody)
	}

	private fun convertFileRequest(
		filename: String,
		multipartBody: MultiValueMap<String, HttpEntity<*>>,
	): ByteArray {

		val uri = LIBRE_OFFICE_ROUTE
		val response = gotenbergClient
			.post()
			.uri(LIBRE_OFFICE_ROUTE)
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
			.body(multipartBody)
			.exchange { request, response ->
				if (response.statusCode.is2xxSuccessful) {
					response.body.readAllBytes()
				} else if (response.statusCode.is4xxClientError) {
					throw IllegalActionException(errorResponse(response, uri), null, ErrorCode.TYPE_DETECTION_OR_CONVERSION_ERROR)
				} else if (response.statusCode.is5xxServerError) {
					throw IllegalActionException(errorResponse(response, uri), null, ErrorCode.TYPE_DETECTION_OR_CONVERSION_ERROR)
				} else {
					throw IllegalActionException(errorResponse(response, uri), null, ErrorCode.TYPE_DETECTION_OR_CONVERSION_ERROR)
				}
			}

		return response

	}

	private fun errorResponse(
		response: RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse,
		uri: String
	): String {
		val trace = response.headers.get(GOTENBERG_TRACE_HEADER)?.first()
		logger.error("Got ${response.statusCode} when requesting post $uri" + " header trace response: ${trace}")

		return "Got ${response.statusCode} when trying to convert file to PDF"
	}

	private class ByteArrayMultipartFile(
		private val filnavn: String,
		private val bytes: ByteArray,
	) : MultipartFile {
		override fun getInputStream() = ByteArrayInputStream(bytes)

		override fun getName() = "files"

		override fun getOriginalFilename() = filnavn

		override fun getContentType() = FiltypeSjekker().detectContentType(bytes)

		override fun isEmpty(): Boolean = bytes.isEmpty()

		override fun getSize() = bytes.size.toLong()

		override fun getBytes() = bytes

		override fun transferTo(dest: File) {
			FileUtils.writeByteArrayToFile(dest, bytes)
		}
	}

}
