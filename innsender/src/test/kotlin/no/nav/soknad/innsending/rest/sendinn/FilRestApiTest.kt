package no.nav.soknad.innsending.rest.sendinn

import io.mockk.clearAllMocks
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.utils.Api
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.Hjelpemetoder.Companion.writeBytesToFile
import no.nav.soknad.innsending.utils.TokenGenerator
import no.nav.soknad.innsending.utils.builders.SkjemaDokumentDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.SkjemaDtoTestBuilder
import no.nav.soknad.pdfutilities.FiltypeSjekker.Companion.imageFileTypes
import no.nav.soknad.pdfutilities.FiltypeSjekker.Companion.officeFileTypes
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.ClassPathResource
import org.springframework.http.*
import org.springframework.util.LinkedMultiValueMap

class FilRestApiTest : ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var restTemplate: TestRestTemplate

	@Autowired
	private lateinit var innsenderMetrics: InnsenderMetrics


	@Value("\${server.port}")
	var serverPort: Int? = 9064

	var testApi: Api? = null
	val api: Api
		get() = testApi!!

	@BeforeEach
	fun setup() {
		testApi = Api(restTemplate, serverPort!!, mockOAuth2Server)
		clearAllMocks()
	}

	@BeforeEach
	fun init() {
		innsenderMetrics.unregisterMetrics()
		innsenderMetrics.registerMetrics()
	}

	private val defaultSkjemanr = "NAV 55-00.60"

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

	private fun lastoppOgvalider(token: String, soknadDto: DokumentSoknadDto, vedleggsId: Long): FilDto? {

		val multipart = LinkedMultiValueMap<Any, Any>()
		multipart.add("file", ClassPathResource("/litenPdf.pdf"))

		val postFilRequest = HttpEntity(multipart, Hjelpemetoder.createHeaders(token, MediaType.MULTIPART_FORM_DATA))
		val postFilResponse = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${soknadDto.innsendingsId!!}/vedlegg/${vedleggsId}/fil",
			HttpMethod.POST,
			postFilRequest,
			FilDto::class.java
		)
		assertEquals(HttpStatus.CREATED, postFilResponse.statusCode)
		assertTrue(postFilResponse.body != null)
		assertEquals(Mimetype.applicationSlashPdf, postFilResponse.body!!.mimetype)
		val opplastetFilDto = postFilResponse.body

		val vedleggN6Request = HttpEntity<Unit>(Hjelpemetoder.createHeaders(token))
		val oppdatertVedleggN6Response = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${soknadDto.innsendingsId!!}/vedlegg/${vedleggsId}",
			HttpMethod.GET,
			vedleggN6Request,
			VedleggDto::class.java
		)

		assertTrue(oppdatertVedleggN6Response.body != null)
		val oppdatertVedleggN6 = oppdatertVedleggN6Response.body
		assertEquals(OpplastingsStatusDto.LastetOpp, oppdatertVedleggN6!!.opplastingsStatus)

		return opplastetFilDto
	}

	private fun slettFilOgValider(token: String, soknadDto: DokumentSoknadDto, vedleggsId: Long, filId: Long, expectedStatus: OpplastingsStatusDto) {
		val slettFilRequest = HttpEntity<Unit>(Hjelpemetoder.createHeaders(token))
		val slettetFilVedleggResponse = restTemplate.exchange(
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
		val skjemanr = defaultSkjemanr
		val spraak = "nb_NO"
		val vedlegg = listOf("N6", "W2")
		val bildeNavnPrefix = "bilde"
		val filPath = "/__files/$bildeNavnPrefix"
		val token = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val soknadDto = opprettEnSoknad(skjemanr, spraak, vedlegg)

		val vedleggN6 = soknadDto.vedleggsListe.first { it.vedleggsnr == "N6" }
		assertEquals(OpplastingsStatusDto.IkkeValgt, vedleggN6.opplastingsStatus)

		assertThrows<Exception> {
			lastOppFil(token, soknadDto.innsendingsId!!, vedleggN6.id!!, filPath+ ".heic")
		}

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
		return restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${innsendingsId!!}/vedlegg/${vedleggsId}/fil",
			HttpMethod.POST,
			httpEntity,
			FilDto::class.java
		)

	}

	private fun hentOpplastetFil(token: String, innsendingsId: String, vedleggsId: Long, filId: Long):ResponseEntity<ByteArrayResource>  {
		val httpEntity = HttpEntity<Unit>(Hjelpemetoder.createHeaders(token))
		return restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/$innsendingsId/vedlegg/$vedleggsId/fil/$filId",
			HttpMethod.GET,
			httpEntity,
			ByteArrayResource::class.java
		)
	}

	private fun slettOpplastetFil(token: String, innsendingsId: String, vedleggsId: Long, filId: Long) {
		val httpEntity = HttpEntity<Unit>(Hjelpemetoder.createHeaders(token))
		val response = restTemplate.exchange(
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
		val skjemanr = defaultSkjemanr
		val spraak = "nb_NO"
		val vedlegg = listOf("N6", "W2")
		val token = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val soknadDto = opprettEnSoknad(skjemanr, spraak, vedlegg)

		val vedleggN6 = soknadDto.vedleggsListe.first { it.vedleggsnr == "N6" }
		assertEquals(OpplastingsStatusDto.IkkeValgt, vedleggN6.opplastingsStatus)

		val multipart = LinkedMultiValueMap<Any, Any>()
		multipart.add("file", ClassPathResource("/pdfs/acroform-fields-tom-array.pdf"))

		val postFilRequestN6 = HttpEntity(multipart, Hjelpemetoder.createHeaders(token, MediaType.MULTIPART_FORM_DATA))

		assertThrows(Exception::class.java) {
			for (i in 1..50) {
				val postFilResponseN6 = restTemplate.exchange(
					"http://localhost:${serverPort}/frontend/v1/soknad/${soknadDto.innsendingsId!!}/vedlegg/${vedleggN6.id}/fil",
					HttpMethod.POST,
					postFilRequestN6,
					FilDto::class.java
				)

				assertEquals(HttpStatus.CREATED, postFilResponseN6.statusCode)
				assertNotNull(postFilResponseN6.body)
				assertEquals(Mimetype.applicationSlashPdf, postFilResponseN6.body!!.mimetype)
			}
		}

	}

	@Test
	fun sjekkAtOpplastingAvUlovligFilformatGirFeilTest() {
		val skjemanr = defaultSkjemanr
		val spraak = "nb_NO"
		val vedlegg = listOf("N6", "W2")
		val token = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val soknadDto = opprettEnSoknad(skjemanr, spraak, vedlegg)

		val vedleggN6 = soknadDto.vedleggsListe.first { it.vedleggsnr == "N6" }
		assertEquals(OpplastingsStatusDto.IkkeValgt, vedleggN6.opplastingsStatus)

		val multipart = LinkedMultiValueMap<Any, Any>()
		multipart.add("file", ClassPathResource("/ikke.jpg"))

		val postFilRequestN6 = HttpEntity(multipart, Hjelpemetoder.createHeaders(token, MediaType.MULTIPART_FORM_DATA))

		var ok = true
		assertThrows(Exception::class.java) {
			restTemplate.exchange(
				"http://localhost:${serverPort}/frontend/v1/soknad/${soknadDto.innsendingsId!!}/vedlegg/${vedleggN6.id}/fil",
				HttpMethod.POST,
				postFilRequestN6,
				FilDto::class.java
			)
			ok = false
		}
		assertTrue(ok)
	}

	@Test
	fun sjekkAtOpplastingAvKryptertFilGirFeilTest() {
		val skjemanr = defaultSkjemanr
		val spraak = "nb_NO"
		val vedlegg = listOf("N6", "W2")
		val token = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val soknadDto = opprettEnSoknad(skjemanr, spraak, vedlegg)

		val vedleggN6 = soknadDto.vedleggsListe.first { it.vedleggsnr == "N6" }
		assertEquals(OpplastingsStatusDto.IkkeValgt, vedleggN6.opplastingsStatus)

		val multipart = LinkedMultiValueMap<Any, Any>()
		multipart.add("file", ClassPathResource("/skjema-passordbeskyttet.pdf"))

		val postFilRequestN6 = HttpEntity(multipart, Hjelpemetoder.createHeaders(token, MediaType.MULTIPART_FORM_DATA))

		assertThrows(Exception::class.java) {
			for (i in 1..100) {
				val postFilResponseN6 = restTemplate.exchange(
					"http://localhost:${serverPort}/frontend/v1/soknad/${soknadDto.innsendingsId!!}/vedlegg/${vedleggN6.id}/fil",
					HttpMethod.POST,
					postFilRequestN6,
					FilDto::class.java
				)

				assertEquals(HttpStatus.CREATED, postFilResponseN6.statusCode)
				assertNotNull(postFilResponseN6.body)
				assertEquals(Mimetype.applicationSlashPdf, postFilResponseN6.body!!.mimetype)
			}
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
