package no.nav.soknad.pdfutilities.gotenberg

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.pdfutilities.FileToPdfInterface
import no.nav.soknad.pdfutilities.FiltypeSjekker
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Service
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Service
class GotenbergConvertToPdf(
	@Qualifier("gotenbergClient")
	private val gotenbergClient: RestClient,
) : FileToPdfInterface {
	companion object GotenbergConsts {
		private const val LIBRE_OFFICE_ROUTE = "/forms/libreoffice/convert"
		private const val FLATTEN_ROUTE = "/forms/libreoffice/convert"
		private const val HTML_ROUTE = "/forms/chromium/convert/html"
		private const val PDF_MERGE_ROUTE = "/forms/pdfengines/merge"
		private const val GOTENBERG_TRACE_HEADER = "gotenberg-trace"

		private const val mergeWithPdfa = false
		private const val mergeWithPdfua = false

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

		return convertFileRequest(fileName, multipartBody, LIBRE_OFFICE_ROUTE)
	}

	override fun imageToPdf(fileName: String, fileContent: ByteArray): ByteArray {
		val htmlString = getHtmlTemplate(fileName)
		val pageProperties: PageProperties = PageProperties.Builder()
			.addMarginLeft(0.1f)
			.addMarginRight(0.1f)
			.addMarginTop(0.1f)
			.addMarginBottom(0.1f)
			.addScale(0.7f)
			.build()
		val multipartBody = MultipartBodyBuilder().run {
			part("files", ByteArrayMultipartFile("index.html", htmlString.toByteArray()).resource)
			part("files", ByteArrayMultipartFile(fileName, fileContent).resource)
			pageProperties.all().forEach{ part(it.key, it.value) }
			build()
		}

		return convertFileRequest(fileName, multipartBody, HTML_ROUTE)

	}

	override fun mergePdfs(fileName: String, docs: List<ByteArray>): ByteArray {
		val pageProperties: PageProperties = PageProperties.Builder().build()
		val multipartBody = MultipartBodyBuilder().run {
			docs.forEach {
				part("files", ByteArrayMultipartFile("merge-"+ UUID.randomUUID().toString()+".pdf", it).resource)
			}
			filter_pdfa_and_or_ua(pageProperties.all()).forEach { part(it.key, it.value) }
			build()
		}

		val start = System.currentTimeMillis()
		val merged =  convertFileRequest(fileName, multipartBody, PDF_MERGE_ROUTE)
		val tidsbruk = System.currentTimeMillis() - start
		logger.debug("Tid brukt på å slå sammen filer=$tidsbruk")

		return merged
	}


	override fun flattenPdfs(fileName: String, meatadata: String, docs: List<ByteArray>): ByteArray {
		val pageProperties: PageProperties = PageProperties.Builder().build()
		val multipartBody = MultipartBodyBuilder().run {
			docs.forEach {
				part("files", ByteArrayMultipartFile("flatten-"+ UUID.randomUUID().toString()+".pdf", it).resource)
			}
			filter_pdfa_and_or_ua(pageProperties.all()).forEach { part(it.key, it.value) }
			part("flatten", true)
			part("metadata", meatadata )
			build()
		}

		val start = System.currentTimeMillis()
		val flatten =  convertFileRequest(fileName, multipartBody, FLATTEN_ROUTE)
		val tidsbruk = System.currentTimeMillis() - start
		logger.debug("Tid brukt på å slå sammen filer=$tidsbruk")

		return flatten
	}


	private fun filter_pdfa_and_or_ua(properties: Map<String, String>): Map<String, String> {
		// pdfa and pdfua is default selected
		return filter_pdfua(filter_pdfa(properties))
	}
	private fun filter_pdfa(properties: Map<String, String>): Map<String, String> {
		return if(!mergeWithPdfa) properties.filter { it.key != "pdfa" } else properties
	}
	private fun filter_pdfua(properties: Map<String, String>): Map<String, String> {
		return if (!mergeWithPdfua) properties.filter { it.key != "pdfua" } else properties
	}

	private fun getHtmlTemplate(fileName: String): String {
		val htmlTemplate = this::class.java.getResource("/pdf/templates/image-to-pdf.html")?.readText()
		if (htmlTemplate.isNullOrEmpty()) {
			throw BackendErrorException("Fant ikke html template for konvertering av opplastet bilde til PDF", null, ErrorCode.GENERAL_ERROR)
		}
		return htmlTemplate.replace("##bilde##", fileName)
	}


	override fun buildMetadata(title: String?, subject: String?, author: String?, keywords: List<String>?): String {
		val now = LocalDateTime.now()
		val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
		val nowString = now.format(formatter)
		val metaDatas = mutableListOf( PdfMetadata("creationDate", nowString), PdfMetadata("modDate", nowString), PdfMetadata("producer", "Gotenberg"))
		if (title != null) metaDatas.add(PdfMetadata("title", title))
		if (subject != null) metaDatas.add(PdfMetadata("subject", subject))
		if (author != null) metaDatas.add(PdfMetadata("author", author))
		if (keywords != null) metaDatas.add(PdfMetadata("keywords", keywords))
		val mapper = jacksonObjectMapper().findAndRegisterModules()

		return mapper.writeValueAsString(metaDatas)
	}

	private fun convertFileRequest(
		filename: String,
		multipartBody: MultiValueMap<String, HttpEntity<*>>,
		route: String
	): ByteArray {

		val uri = route
		logger.info( "Calling Gotenberg route=$uri")
		val response = gotenbergClient
			.post()
			.uri(uri)
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
			.body(multipartBody)
			.exchange { request, response ->

				if (response.statusCode.is2xxSuccessful) {
					response.body.readAllBytes()
				} else if (response.statusCode.is4xxClientError) {
					logger.warn("Gotenberg Client side error status = ${response.statusCode}")
					throw IllegalActionException(errorResponse(response, uri), null, ErrorCode.TYPE_DETECTION_OR_CONVERSION_ERROR)
				} else if (response.statusCode.is5xxServerError) {
					logger.warn("Gotenberg Server side error status = ${response.statusCode}")
					throw IllegalActionException(errorResponse(response, uri), null, ErrorCode.TYPE_DETECTION_OR_CONVERSION_ERROR)
				} else {
					logger.warn("Gotenberg call error status = ${response.statusCode}")
					throw IllegalActionException(errorResponse(response, uri), null, ErrorCode.TYPE_DETECTION_OR_CONVERSION_ERROR)
				}
			}

		if (response == null) {
			throw IllegalActionException("Got empty response when requesting $uri", null, ErrorCode.TYPE_DETECTION_OR_CONVERSION_ERROR)
		}
		logger.info("Called Gotenberg route=$uri with response.size=${response.size}")
		return response

	}

	private fun errorResponse(
		response: RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse,
		uri: String
	): String {
		val trace = response.headers.get(GOTENBERG_TRACE_HEADER)?.first()
		if (response.statusCode.is4xxClientError) {
			logger.error("Got ${response.statusCode} when requesting post $uri" + " header trace response: ${trace}")
		} else {
			logger.warn("Got ${response.statusCode} when requesting post $uri" + " header trace response: ${trace}")
		}

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
