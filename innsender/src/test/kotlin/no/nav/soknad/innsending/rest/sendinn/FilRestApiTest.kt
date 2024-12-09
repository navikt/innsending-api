package no.nav.soknad.innsending.rest.sendinn

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.Hjelpemetoder.Companion.writeBytesToFile
import no.nav.soknad.innsending.utils.TokenGenerator
import no.nav.soknad.pdfutilities.FiltypeSjekker.Companion.imageFileTypes
import no.nav.soknad.pdfutilities.FiltypeSjekker.Companion.officeFileTypes
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
	lateinit var soknadService: SoknadService

	@Autowired
	private lateinit var innsenderMetrics: InnsenderMetrics


	@Value("\${server.port}")
	var serverPort: Int? = 9064

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

		val soknadDto = opprettEnSoknad(token, skjemanr, spraak, vedlegg)

		val vedleggN6 = soknadDto.vedleggsListe.first { it.vedleggsnr == "N6" }
		assertEquals(OpplastingsStatusDto.IkkeValgt, vedleggN6.opplastingsStatus)

		val multipart = LinkedMultiValueMap<Any, Any>()
		multipart.add("file", ClassPathResource("/litenPdf.pdf"))

		val postFilRequestN6 = HttpEntity(multipart, Hjelpemetoder.createHeaders(token, MediaType.MULTIPART_FORM_DATA))
		val postFilResponseN6 = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${soknadDto.innsendingsId!!}/vedlegg/${vedleggN6.id}/fil",
			HttpMethod.POST,
			postFilRequestN6,
			FilDto::class.java
		)
		val filePages = innsenderMetrics.fileNumberOfPagesSummary
		val fileSize = innsenderMetrics.fileSizeSummary

		assertEquals(HttpStatus.CREATED, postFilResponseN6.statusCode)
		assertTrue(postFilResponseN6.body != null)
		assertEquals(Mimetype.applicationSlashPdf, postFilResponseN6.body!!.mimetype)
		assertEquals(1.0, filePages.collect().dataPoints[0].sum)
		assertEquals(7187.0, fileSize.collect().dataPoints[0].sum)
		val opplastetFilDto = postFilResponseN6.body

		val vedleggN6Request = HttpEntity<Unit>(Hjelpemetoder.createHeaders(token))
		val oppdatertVedleggN6Response = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${soknadDto.innsendingsId!!}/vedlegg/${vedleggN6.id}",
			HttpMethod.GET,
			vedleggN6Request,
			VedleggDto::class.java
		)

		assertTrue(oppdatertVedleggN6Response.body != null)
		val oppdatertVedleggN6 = oppdatertVedleggN6Response.body
		assertEquals(OpplastingsStatusDto.LastetOpp, oppdatertVedleggN6!!.opplastingsStatus)

		val slettFilRequest = HttpEntity<Unit>(Hjelpemetoder.createHeaders(token))
		val slettetFilVedleggN6Response = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${soknadDto.innsendingsId!!}/vedlegg/${vedleggN6.id}/fil/${opplastetFilDto!!.id}",
			HttpMethod.DELETE,
			slettFilRequest,
			VedleggDto::class.java
		)

		assertEquals(HttpStatus.OK, slettetFilVedleggN6Response.statusCode)
		assertTrue(slettetFilVedleggN6Response.body != null)
		val oppdatertEtterSlettetFilVedleggN6 = slettetFilVedleggN6Response.body
		assertEquals(OpplastingsStatusDto.IkkeValgt, oppdatertEtterSlettetFilVedleggN6!!.opplastingsStatus)

	}


	@Test
	fun verifiserOpplastingAvUlikeFiltyperTest() {
		val skjemanr = defaultSkjemanr
		val spraak = "nb-NO"
		val vedlegg = listOf("N6", "W2")
		val token = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val soknadDto = opprettEnSoknad(token, skjemanr, spraak, vedlegg)

		val vedleggN6 = soknadDto.vedleggsListe.first { it.vedleggsnr == "N6" }
		assertEquals(OpplastingsStatusDto.IkkeValgt, vedleggN6.opplastingsStatus)

		testOpplastingAvOfficeFormater(token, soknadDto.innsendingsId!!, vedleggN6.id!!)

		val vedleggW2 = soknadDto.vedleggsListe.first { it.vedleggsnr == "W2" }
		assertEquals(OpplastingsStatusDto.IkkeValgt, vedleggW2.opplastingsStatus)

		testOpplastingAvBildeFormater(token, soknadDto.innsendingsId!!, vedleggN6.id!!)

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

		val soknadDto = opprettEnSoknad(token, skjemanr, spraak, vedlegg)

		val vedleggN6 = soknadDto.vedleggsListe.first { it.vedleggsnr == "N6" }
		assertEquals(OpplastingsStatusDto.IkkeValgt, vedleggN6.opplastingsStatus)

		org.junit.jupiter.api.assertThrows<Exception> {
			lastOppFil(token, soknadDto.innsendingsId!!, vedleggN6.id!!, filPath+ ".heic")
		}

	}

	private fun testOpplastingAvBildeFormater(token: String, innsendingsId: String, vedleggsId: Long) {
		val bildeNavnPrefix = "bilde"
		val filPath = "/__files/$bildeNavnPrefix"

		imageFileTypes.keys.forEach { lastOppOgSjekk(token, innsendingsId, vedleggsId,  filPath, it) }
	}

	private fun testOpplastingAvOfficeFormater(token: String, innsendingsId: String, vedleggsId: Long) {
		val officeNavnPrefix = "office"
		val filPath = "/__files/$officeNavnPrefix"

		officeFileTypes.keys.forEach { lastOppOgSjekk(token, innsendingsId, vedleggsId,  filPath, it) }
	}

	private fun lastOppOgSjekk(token: String, innsendingsId: String, vedleggsId: Long, filSti: String, type: String) {
		val filDto = lastOppFil(token, innsendingsId, vedleggsId, filSti+type)
		assertEquals(HttpStatus.CREATED, filDto.statusCode)
		assertTrue(filDto.body != null)
		assertEquals(Mimetype.applicationSlashPdf, filDto.body!!.mimetype)
		assertNotNull(filDto.body!!.id)

		val opplastetFil = hentOpplastetFil(token, innsendingsId, filDto.body?.vedleggsid!!, filDto.body?.id!!)
		assertEquals(HttpStatus.OK, opplastetFil.statusCode)
		assertEquals(filDto.body!!.storrelse, opplastetFil.body!!.byteArray.size)

		opplastetFil.body?.let { writeBytesToFile(it.byteArray, "target/delme-$type.pdf") }

		slettOpplastetFil(token, innsendingsId, filDto.body?.vedleggsid!!, filDto.body?.id!!)

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

		val soknadDto = opprettEnSoknad(token, skjemanr, spraak, vedlegg)

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

		val soknadDto = opprettEnSoknad(token, skjemanr, spraak, vedlegg)

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

		val soknadDto = opprettEnSoknad(token, skjemanr, spraak, vedlegg)

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
		token: String,
		skjemanr: String,
		spraak: String,
		vedlegg: List<String>
	): DokumentSoknadDto {

		val opprettSoknadBody = OpprettSoknadBody(skjemanr, spraak, vedlegg)
		val postRequestEntity = HttpEntity(opprettSoknadBody, Hjelpemetoder.createHeaders(token))

		val postResponse = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad", HttpMethod.POST,
			postRequestEntity, DokumentSoknadDto::class.java
		)

		assertTrue(postResponse.body != null)
		val opprettetSoknadDto = postResponse.body
		assertTrue(opprettetSoknadDto!!.vedleggsListe.isNotEmpty())

		return opprettetSoknadDto
	}
}
