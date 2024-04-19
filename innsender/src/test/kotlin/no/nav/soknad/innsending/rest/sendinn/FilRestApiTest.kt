package no.nav.soknad.innsending.rest.sendinn

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.TokenGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
		innsenderMetrics.clearFileSize()
		innsenderMetrics.clearFileNumberOfPages()
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
		assertEquals(OpplastingsStatusDto.ikkeValgt, vedleggN6.opplastingsStatus)

		val multipart = LinkedMultiValueMap<Any, Any>()
		multipart.add("file", ClassPathResource("/litenPdf.pdf"))

		val postFilRequestN6 = HttpEntity(multipart, Hjelpemetoder.createHeaders(token, MediaType.MULTIPART_FORM_DATA))
		val postFilResponseN6 = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${soknadDto.innsendingsId!!}/vedlegg/${vedleggN6.id}/fil",
			HttpMethod.POST,
			postFilRequestN6,
			FilDto::class.java
		)
		val filePages = innsenderMetrics.getFileNumberOfPages()
		val fileSize = innsenderMetrics.getFileSize()

		assertEquals(HttpStatus.CREATED, postFilResponseN6.statusCode)
		assertTrue(postFilResponseN6.body != null)
		assertEquals(Mimetype.applicationSlashPdf, postFilResponseN6.body!!.mimetype)
		assertEquals(1.0, filePages.sum)
		assertEquals(7187.0, fileSize.sum)
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
		assertEquals(OpplastingsStatusDto.lastetOpp, oppdatertVedleggN6!!.opplastingsStatus)

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
		assertEquals(OpplastingsStatusDto.ikkeValgt, oppdatertEtterSlettetFilVedleggN6!!.opplastingsStatus)

	}

	@Test
	fun sjekkAtOpplastingAvForStorFilGirFeilTest() {
		val skjemanr = defaultSkjemanr
		val spraak = "nb_NO"
		val vedlegg = listOf("N6", "W2")
		val token = TokenGenerator(mockOAuth2Server).lagTokenXToken()

		val soknadDto = opprettEnSoknad(token, skjemanr, spraak, vedlegg)

		val vedleggN6 = soknadDto.vedleggsListe.first { it.vedleggsnr == "N6" }
		assertEquals(OpplastingsStatusDto.ikkeValgt, vedleggN6.opplastingsStatus)

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
		assertEquals(OpplastingsStatusDto.ikkeValgt, vedleggN6.opplastingsStatus)

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
		assertEquals(OpplastingsStatusDto.ikkeValgt, vedleggN6.opplastingsStatus)

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
