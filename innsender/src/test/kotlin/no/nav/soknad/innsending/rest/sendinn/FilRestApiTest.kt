package no.nav.soknad.innsending.rest.sendinn

import com.ninjasquad.springmockk.MockkSpyBean
import io.mockk.clearAllMocks
import io.mockk.verify
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.service.FilService
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.utils.ApiWebClient
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.TokenGenerator
import no.nav.soknad.innsending.utils.builders.SkjemaDokumentDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.SkjemaDtoTestBuilder
import no.nav.soknad.pdfutilities.FiltypeSjekker.Companion.imageFileTypes
import no.nav.soknad.pdfutilities.FiltypeSjekker.Companion.officeFileTypes
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.ClassPathResource
import org.springframework.http.*
import org.springframework.util.LinkedMultiValueMap
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FilRestApiTest : ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	private lateinit var innsenderMetrics: InnsenderMetrics

	@MockkSpyBean
	private lateinit var filService: FilService

	@LocalServerPort
	var serverPort: Int = 0

	var testApi: ApiWebClient? = null
	val api: ApiWebClient
		get() = testApi!!

	@BeforeEach
	fun setup() {
		testApi = ApiWebClient(webTestClient, serverPort, mockOAuth2Server)
		clearAllMocks()
	}

	@BeforeEach
	fun init() {
		innsenderMetrics.unregisterMetrics()
		innsenderMetrics.registerMetrics()
	}

	private val defaultSkjemanr = "NAV 55-00.60"

	@Test
	fun `missing required file part returns dedicated bad request error`() {
		val token = TokenGenerator(mockOAuth2Server).lagTokenXToken()
		val soknadDto = opprettEnSoknad(defaultSkjemanr, "nb_NO", listOf("N6"))
		val vedleggsId = soknadDto.vedleggsListe.first { it.vedleggsnr == "N6" }.id!!
		val multipartWithoutFile = LinkedMultiValueMap<Any, Any>().apply {
			add("metadata", "missing file")
		}
		clearAllMocks()

		val response = restTestClient.exchange(
			"http://localhost:$serverPort/frontend/v1/soknad/${soknadDto.innsendingsId}/vedlegg/$vedleggsId/fil",
			HttpMethod.POST,
			HttpEntity(
				multipartWithoutFile,
				Hjelpemetoder.createHeaders(token, MediaType.MULTIPART_FORM_DATA)
			),
			RestErrorResponseDto::class.java
		)

		assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
		assertEquals("invalidRequest.missingMultipartPart", response.body?.errorCode)
		verify(exactly = 0) {
			filService.lagreFil(any<DokumentSoknadDto>(), any<FilDto>(), any<Boolean>())
		}
	}

	@Test
	fun sjekkOpplastingsstatusEtterOpplastingOgSlettingAvFilPaVedleggTest() {
		val skjemanr = defaultSkjemanr
		val spraak = "nb_NO"
		val vedlegg = listOf("N6", "W2")
		val token = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val soknadDto = opprettEnSoknad(skjemanr, spraak, vedlegg)

		val vedleggN6 = soknadDto.vedleggsListe.first { it.vedleggsnr == "N6" }
		assertEquals(OpplastingsStatusDto.IkkeValgt, vedleggN6.opplastingsStatus)

		val filePages = innsenderMetrics.fileNumberOfPagesSummary
		val fileSize = innsenderMetrics.fileSizeSummary

		val opplastetFilDto = lastoppOgvalider(token, soknadDto, vedleggN6.id!! )
		val opplastetFilDto2 = lastoppOgvalider(token, soknadDto, vedleggN6.id!! )

		assertEquals(2.0, filePages.collect().dataPoints[0].sum)
		assertEquals(2*7187.0, fileSize.collect().dataPoints[0].sum)

		slettFilOgValider(token, soknadDto, vedleggN6.id!!, opplastetFilDto!!.id!!, OpplastingsStatusDto.LastetOpp )
		slettFilOgValider(token, soknadDto, vedleggN6.id!!, opplastetFilDto2!!.id!!, OpplastingsStatusDto.IkkeValgt )

	}

	private fun lastoppOgvalider(token: String, soknadDto: DokumentSoknadDto, vedleggsId: Long, filPath: String = "/litenPdf.pdf" ): FilDto? {

		val multipart = LinkedMultiValueMap<Any, Any>()
		multipart.add("file", ClassPathResource(filPath))

		val postFilRequest = HttpEntity(multipart, Hjelpemetoder.createHeaders(token, MediaType.MULTIPART_FORM_DATA))
		val postFilResponse = restTestClient.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${soknadDto.innsendingsId!!}/vedlegg/${vedleggsId}/fil",
			HttpMethod.POST,
			postFilRequest,
			FilDto::class.java
		)
		assertEquals(HttpStatus.CREATED, postFilResponse.statusCode)
		assertTrue(postFilResponse.body != null)
		assertEquals(Mimetype.applicationSlashPdf, postFilResponse.body!!.mimetype)
		val opplastetFilDto = postFilResponse.body

		val oppdatertSoknad = api.getSoknadSendinn(soknadDto.innsendingsId!!)
			.assertSuccess()
			.body
		val oppdatertVedleggN6 = oppdatertSoknad.vedleggsListe.first { it.id == vedleggsId }
		assertEquals(OpplastingsStatusDto.LastetOpp, oppdatertVedleggN6.opplastingsStatus)

		return opplastetFilDto
	}

	private fun slettFilOgValider(token: String, soknadDto: DokumentSoknadDto, vedleggsId: Long, filId: Long, expectedStatus: OpplastingsStatusDto) {
		val slettFilRequest = HttpEntity<Unit>(Hjelpemetoder.createHeaders(token))
		val slettetFilVedleggResponse = restTestClient.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${soknadDto.innsendingsId!!}/vedlegg/${vedleggsId}/fil/${filId}",
			HttpMethod.DELETE,
			slettFilRequest,
			VedleggDto::class.java
		)

		assertEquals(HttpStatus.OK, slettetFilVedleggResponse.statusCode)
		assertTrue(slettetFilVedleggResponse.body != null)
		val oppdatertEtterSlettetFilVedlegg = slettetFilVedleggResponse.body
		assertEquals(expectedStatus, oppdatertEtterSlettetFilVedlegg!!.opplastingsStatus)

	}

	@Test
	fun verifiserOpplastingAvUlikeFiltyperTest() =
		runBlocking {
			val awaits: MutableList<Deferred<Unit>> = mutableListOf()
			awaits.add(async(Dispatchers.IO) {	testOpplastingAvOfficeFormater() })
			awaits.add(async(Dispatchers.IO) {	testOpplastingAvBildeFormater()	})
			awaits.awaitAll()

			val filePages = innsenderMetrics.fileNumberOfPagesSummary
			val fileSize = innsenderMetrics.fileSizeSummary
			assertTrue(filePages.collect().dataPoints[0].sum > 1.0)
			assertTrue(fileSize.collect().dataPoints[0].sum > 30000.0)
		}


	@Test
	fun verifiserAvvisningAvUlovligeFiltyper() {
		val soknadDto = opprettEnSoknad(defaultSkjemanr, "nb_NO", listOf("N6", "W2"))
		val vedleggN6 = soknadDto.vedleggsListe.first { it.vedleggsnr == "N6" }
		assertEquals(OpplastingsStatusDto.IkkeValgt, vedleggN6.opplastingsStatus)

		api.uploadFile(
			soknadDto.innsendingsId!!,
			vedleggN6.id!!,
			Hjelpemetoder.getBytesFromFile("/__files/bilde.heic"),
			fileName = "bilde.heic",
		)
			.assertHttpStatus(HttpStatus.BAD_REQUEST)
			.assertErrorCode(ErrorCode.NOT_SUPPORTED_FILE_FORMAT)

		assertVedleggIkkeValgt(soknadDto.innsendingsId!!, vedleggN6.id!!)
	}

	private fun testOpplastingAvBildeFormater() {
		val bildeNavnPrefix = "bilde"
		val filPath = "/__files/$bildeNavnPrefix"

		runBlocking {
			val awaits: MutableList<Deferred<Unit>> = mutableListOf()
			val start = System.currentTimeMillis()
			imageFileTypes.keys.forEach { awaits.add(async(Dispatchers.IO) { lastOppOgSjekk(filPath, it) }) }
			awaits.awaitAll()
			val time = System.currentTimeMillis() - start
			System.out.println("Time for lastOppOgSjekk for $bildeNavnPrefix: $time")
		}
	}

	private suspend fun testOpplastingAvOfficeFormater() {
		val officeNavnPrefix = "office"
		val filPath = "/__files/$officeNavnPrefix"

		runBlocking {
			val awaits: MutableList<Deferred<Unit>> = mutableListOf()
			val start = System.currentTimeMillis()
			officeFileTypes.keys.forEach { awaits.add(async(Dispatchers.IO) { lastOppOgSjekk(filPath, it) }) }
			val time = System.currentTimeMillis() - start
			System.out.println("Time for lastOppOgSjekk for $officeNavnPrefix: $time")
		}
	}

	private fun lastOppOgSjekk( filSti: String, type: String) {
		val skjemanr = defaultSkjemanr
		val spraak = "nb-NO"
		val vedlegg = listOf("W2")
		val token = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val soknadDto = opprettEnSoknad(skjemanr, spraak, vedlegg)
		val vedleggW2 = soknadDto.vedleggsListe.first { it.vedleggsnr == "W2" }.id

		val filDto = lastOppFil(token, soknadDto.innsendingsId!!, vedleggW2!!, filSti+type)
		assertEquals(HttpStatus.CREATED, filDto.statusCode)
		assertTrue(filDto.body != null)
		assertEquals(Mimetype.applicationSlashPdf, filDto.body!!.mimetype)
		assertNotNull(filDto.body!!.id)

		val opplastetFil = hentOpplastetFil(token, soknadDto.innsendingsId!!, filDto.body?.vedleggsid!!, filDto.body?.id!!)
		assertEquals(HttpStatus.OK, opplastetFil.statusCode)
		assertEquals(filDto.body!!.storrelse, opplastetFil.body!!.byteArray.size)

//		opplastetFil.body?.let { writeBytesToFile(it.byteArray, "target/delme-$type.pdf") }


	}

	private fun lastOppFil(token: String, innsendingsId: String, vedleggsId: Long, filePath: String): ResponseEntity<FilDto> {
		val multipart = LinkedMultiValueMap<Any, Any>()
		multipart.add("file", ClassPathResource(filePath))

		val httpEntity = HttpEntity(multipart, Hjelpemetoder.createHeaders(token, MediaType.MULTIPART_FORM_DATA))
		return restTestClient.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${innsendingsId!!}/vedlegg/${vedleggsId}/fil",
			HttpMethod.POST,
			httpEntity,
			FilDto::class.java
		)

	}

	private fun hentOpplastetFil(token: String, innsendingsId: String, vedleggsId: Long, filId: Long):ResponseEntity<ByteArrayResource>  {
/*
		val httpEntity = HttpEntity<Unit>(
			Hjelpemetoder.createHeaders(
				token,
				mapOf(HttpHeaders.ACCEPT to "${MediaType.APPLICATION_PDF_VALUE}, ${MediaType.APPLICATION_JSON_VALUE}")
			)
		)
		return restTestClient.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/$innsendingsId/vedlegg/$vedleggsId/fil/$filId",
			HttpMethod.GET,
			httpEntity,
			ByteArrayResource::class.java,

		)
*/
		val filResponsBytes = webTestClient.get()
			.uri("http://localhost:${serverPort}/frontend/v1/soknad/$innsendingsId/vedlegg/$vedleggsId/fil/$filId")
			.headers { headers ->
				headers.setAll(Hjelpemetoder.createHeaders(token, MediaType.APPLICATION_PDF).toSingleValueMap())
			}
			.accept(MediaType.APPLICATION_PDF)
			.exchange()
			.expectStatus().isOk
			.expectHeader().contentType(MediaType.APPLICATION_PDF)
			.expectBody(ByteArray::class.java)
			.returnResult()
			.responseBody

		// Assertions
		assertNotNull(filResponsBytes, "Responsen skal ikke være null")
		assertTrue(filResponsBytes.isNotEmpty(), "PDF-filen skal inneholde data")
		return ResponseEntity(ByteArrayResource(filResponsBytes), HttpHeaders().apply {
			contentType = MediaType.APPLICATION_PDF
		}, HttpStatus.OK)

	}

	private fun slettOpplastetFil(token: String, innsendingsId: String, vedleggsId: Long, filId: Long) {
		val httpEntity = HttpEntity<Unit>(Hjelpemetoder.createHeaders(token))
		val response = restTestClient.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${innsendingsId}/vedlegg/$vedleggsId/fil/$filId",
			HttpMethod.DELETE,
			httpEntity,
			VedleggDto::class.java
		)

		assertEquals(HttpStatus.OK, response.statusCode)
		assertTrue(response.body != null)
		val oppdatertEtterSlettetFilVedleggN6 = response.body
		assertEquals(OpplastingsStatusDto.IkkeValgt, oppdatertEtterSlettetFilVedleggN6!!.opplastingsStatus)
	}

	@Test
	fun sjekkAtOpplastingAvForStorFilGirFeilTest() {
		val soknadDto = opprettEnSoknad(defaultSkjemanr, "nb_NO", listOf("N6", "W2"))
		val vedleggN6 = soknadDto.vedleggsListe.first { it.vedleggsnr == "N6" }
		assertEquals(OpplastingsStatusDto.IkkeValgt, vedleggN6.opplastingsStatus)
		val fil = Hjelpemetoder.getBytesFromFile("/pdfs/acroform-fields-tom-array.pdf")

		// The limit applies to the stored, converted size. Upload the same file until the attachment exceeds 15 MB.
		val responses = generateSequence {
			api.uploadFile(soknadDto.innsendingsId!!, vedleggN6.id!!, fil, fileName = "acroform-fields-tom-array.pdf")
		}.take(20).takeWhileInclusive { it.statusCode.is2xxSuccessful }.toList()

		responses.dropLast(1).forEach { it.assertHttpStatus(HttpStatus.CREATED) }
		assertFalse(responses.last().statusCode.is2xxSuccessful, "Expected the upload to be rejected within 20 attempts")
		responses.last()
			.assertHttpStatus(HttpStatus.BAD_REQUEST)
			.assertErrorCode(ErrorCode.VEDLEGG_FILE_SIZE_SUM_TOO_LARGE)
	}

	@Test
	fun sjekkAtOpplastingAvUlovligFilformatGirFeilTest() {
		val soknadDto = opprettEnSoknad(defaultSkjemanr, "nb_NO", listOf("N6", "W2"))
		val vedleggN6 = soknadDto.vedleggsListe.first { it.vedleggsnr == "N6" }
		assertEquals(OpplastingsStatusDto.IkkeValgt, vedleggN6.opplastingsStatus)

		// ikke.jpg is a JPEG 2000 file with a .jpg extension
		api.uploadFile(
			soknadDto.innsendingsId!!,
			vedleggN6.id!!,
			Hjelpemetoder.getBytesFromFile("/ikke.jpg"),
			fileName = "ikke.jpg",
		)
			.assertHttpStatus(HttpStatus.BAD_REQUEST)
			.assertErrorCode(ErrorCode.NOT_SUPPORTED_FILE_FORMAT)

		assertVedleggIkkeValgt(soknadDto.innsendingsId!!, vedleggN6.id!!)
	}

	@Test
	fun sjekkAtOpplastingAvKryptertFilGirFeilTest() {
		val soknadDto = opprettEnSoknad(defaultSkjemanr, "nb_NO", listOf("N6", "W2"))
		val vedleggN6 = soknadDto.vedleggsListe.first { it.vedleggsnr == "N6" }
		assertEquals(OpplastingsStatusDto.IkkeValgt, vedleggN6.opplastingsStatus)

		api.uploadFile(
			soknadDto.innsendingsId!!,
			vedleggN6.id!!,
			Hjelpemetoder.getBytesFromFile("/skjema-passordbeskyttet.pdf"),
			fileName = "skjema-passordbeskyttet.pdf",
		)
			.assertHttpStatus(HttpStatus.BAD_REQUEST)
			.assertErrorCode(ErrorCode.FILE_CANNOT_BE_READ)

		assertVedleggIkkeValgt(soknadDto.innsendingsId!!, vedleggN6.id!!)
	}

	private fun assertVedleggIkkeValgt(innsendingsId: String, vedleggsId: Long) {
		val vedlegg = api.getSoknadSendinn(innsendingsId).assertSuccess().body.vedleggsListe.first { it.id == vedleggsId }
		assertEquals(OpplastingsStatusDto.IkkeValgt, vedlegg.opplastingsStatus)
	}

	private fun <T> Sequence<T>.takeWhileInclusive(predicate: (T) -> Boolean): Sequence<T> = sequence {
		for (item in this@takeWhileInclusive) {
			yield(item)
			if (!predicate(item)) break
		}
	}

	private fun opprettEnSoknad(
		skjemanr: String,
		spraak: String,
		vedlegg: List<String>
	): DokumentSoknadDto {
		val requestBody = SkjemaDtoTestBuilder(
			skjemanr = skjemanr,
			spraak = spraak,
			vedleggsListe = vedlegg.map { SkjemaDokumentDtoTestBuilder(vedleggsnr = it).build() }
		).build()
		val dokumentSoknadDto = api.createSoknad(requestBody)
			.assertSuccess()
			.body
		return api.getSoknadSendinn(dokumentSoknadDto.innsendingsId!!).assertSuccess().body
	}
}
