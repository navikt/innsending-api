package no.nav.soknad.pdfutilities.gotenberg

import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.pdfutilities.DocxToPdfInterface
import no.nav.soknad.pdfutilities.FiltypeSjekker
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
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
	@Value("\${fil-konvertering_url}") private val baseUrl: String,
) : DocxToPdfInterface {
	companion object GotenbergConsts {
		private const val LIBRE_OFFICE_ROUTE = "/forms/libreoffice/convert"
		private const val GOTENBERG_TRACE_HEADER = "gotenberg-trace"
		private const val pdfa = "--form pdfa=PDF/A-2b"
		private const val ua = "--form pdfua=true"
	}

	private val logger = LoggerFactory.getLogger(GotenbergConvertToPdf::class.java)

	override fun toPdf(
		fileName: String,
		fileContent: ByteArray,
	): ByteArray {
		val multipartBody = MultipartBodyBuilder().run {
			part("files", ByteArrayMultipartFile(fileName, fileContent).resource)
			build()
		}

		return convertFileRequest(fileName, multipartBody)
	}

	private fun convertFileRequest(
		filename: String,
		multipartBody: MultiValueMap<String, HttpEntity<*>>,
	): ByteArray {

		val uri = LIBRE_OFFICE_ROUTE + pdfa + ua
		val response = gotenbergClient
			.post()
			.uri(LIBRE_OFFICE_ROUTE)
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
			.body(multipartBody)
			.exchange { request, response ->
				if (response.statusCode.is2xxSuccessful) {
					response.body.readAllBytes()
				} else if (response.statusCode.is4xxClientError) {
					throw BackendErrorException(errorResponse(response, uri))
				} else if (response.statusCode.is5xxServerError) {
					throw BackendErrorException(errorResponse(response, uri))
				} else {
					throw BackendErrorException(errorResponse(response, uri))
				}
			}

		return response

	}

	private fun errorResponse(
		response: RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse,
		uri: String
	): String {
		val trace = response.headers.get(GOTENBERG_TRACE_HEADER)?.first()
		val msg = "Got ${response.statusCode} when requesting post $uri" + " header trace response: ${trace}"
		logger.error(msg)
		return msg
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
