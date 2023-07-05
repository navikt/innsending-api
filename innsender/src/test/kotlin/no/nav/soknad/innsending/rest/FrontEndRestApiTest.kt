package no.nav.soknad.innsending.rest

import com.google.gson.Gson
import com.nimbusds.jose.JOSEObjectType
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.security.token.support.spring.test.MockLoginController
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.InnsendingApiApplication
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.pdl.generated.enums.IdentGruppe
import no.nav.soknad.innsending.pdl.generated.hentpersoninfo.IdentInformasjon
import no.nav.soknad.innsending.pdl.generated.hentpersoninfo.Identliste
import no.nav.soknad.innsending.pdl.generated.hentpersoninfo.Navn
import no.nav.soknad.innsending.pdl.generated.hentpersoninfo.Person
import no.nav.soknad.innsending.utils.Hjelpemetoder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.util.LinkedMultiValueMap
import java.util.*


@Suppress("DEPRECATION")
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
	properties = ["spring.main.allow-bean-definition-overriding=true"],
	classes = [InnsendingApiApplication::class]
)
@ExtendWith(SpringExtension::class)
class FrontEndRestApiTest : ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var restTemplate: TestRestTemplate


	@Value("\${server.port}")
	var serverPort: Int? = 9064

	private val tokenx = "tokenx"
	private val subject = "12345678901"
	private val audience = "aud-localhost"
	private val expiry = 2 * 3600
	private val defaultUser = "12345678901"
	private val defaultSkjemanr = "NAV 55-00.60"


	@Test
	fun opprettSoknadTest() {
		val skjemanr = defaultSkjemanr
		val spraak = "nb_NO"

		val token: String = mockOAuth2Server.issueToken(
			tokenx,
			MockLoginController::class.java.simpleName,
			DefaultOAuth2TokenCallback(
				tokenx,
				subject,
				JOSEObjectType.JWT.type,
				listOf(audience),
				mapOf("acr" to "Level4"),
				expiry.toLong()
			)
		).serialize()

		val opprettSoknadBody = OpprettSoknadBody(skjemanr, spraak)
		val requestEntity = HttpEntity(opprettSoknadBody, Hjelpemetoder.createHeaders(token))

		val response = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad", HttpMethod.POST,
			requestEntity, DokumentSoknadDto::class.java
		)

		assertTrue(response.body != null)

	}

	@Test
	fun hentOpprettetSoknadTest() {
		val skjemanr = defaultSkjemanr
		val spraak = "nb_NO"

		val token: String = mockOAuth2Server.issueToken(
			tokenx,
			MockLoginController::class.java.simpleName,
			DefaultOAuth2TokenCallback(
				tokenx,
				subject,
				JOSEObjectType.JWT.type,
				listOf(audience),
				mapOf("acr" to "Level4"),
				expiry.toLong()
			)
		).serialize()

		val opprettSoknadBody = OpprettSoknadBody(skjemanr, spraak)
		val postRequestEntity = HttpEntity(opprettSoknadBody, Hjelpemetoder.createHeaders(token))

		val postResponse = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad", HttpMethod.POST,
			postRequestEntity, DokumentSoknadDto::class.java
		)

		assertTrue(postResponse.body != null)

		val opprettetSoknadDto = postResponse.body
		val getRequestEntity = HttpEntity<Unit>(Hjelpemetoder.createHeaders(token))
		val getResponse = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${opprettetSoknadDto!!.innsendingsId}", HttpMethod.GET,
			getRequestEntity, DokumentSoknadDto::class.java
		)

		assertTrue(getResponse.body != null)
		val getSoknadDto = getResponse.body
		assertEquals(opprettetSoknadDto.innsendingsId, getSoknadDto!!.innsendingsId)
	}

	@Test
	fun oppdaterVedleggTest() {
		val skjemanr = defaultSkjemanr
		val spraak = "nb_NO"
		val vedlegg = listOf("N6")

		val token: String = mockOAuth2Server.issueToken(
			tokenx,
			MockLoginController::class.java.simpleName,
			DefaultOAuth2TokenCallback(
				tokenx,
				subject,
				JOSEObjectType.JWT.type,
				listOf(audience),
				mapOf("acr" to "Level4"),
				expiry.toLong()
			)
		).serialize()

		val opprettSoknadBody = OpprettSoknadBody(skjemanr, spraak, vedlegg)
		val postRequestEntity = HttpEntity(opprettSoknadBody, Hjelpemetoder.createHeaders(token))

		val postResponse = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad", HttpMethod.POST,
			postRequestEntity, DokumentSoknadDto::class.java
		)

		assertTrue(postResponse.body != null)
		val opprettetSoknadDto = postResponse.body
		assertTrue(opprettetSoknadDto!!.vedleggsListe.isNotEmpty())

		val vedleggDto = opprettetSoknadDto.vedleggsListe.first { !it.erHoveddokument }
		val patchVedleggDto = PatchVedleggDto("Endret tittel", OpplastingsStatusDto.sendesAvAndre)
		val patchRequestEntity = HttpEntity(patchVedleggDto, Hjelpemetoder.createHeaders(token))
		val patchResponse = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${opprettetSoknadDto.innsendingsId}/vedlegg/${vedleggDto.id}",
			HttpMethod.PATCH,
			patchRequestEntity,
			VedleggDto::class.java
		)

		assertTrue(patchResponse.body != null)
		val patchedVedleggDto = patchResponse.body
		assertEquals(vedleggDto.id, patchedVedleggDto!!.id)
		assertEquals("Endret tittel", patchedVedleggDto.tittel)
		assertEquals(OpplastingsStatusDto.sendesAvAndre, patchedVedleggDto.opplastingsStatus)
	}

	@Test
	fun sjekkOpplastingsstatusEtterOpplastingOgSlettingAvFilPaVedleggTest() {
		val skjemanr = defaultSkjemanr
		val spraak = "nb_NO"
		val vedlegg = listOf("N6", "W2")
		val token = getToken()

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

		assertEquals(HttpStatus.CREATED, postFilResponseN6.statusCode)
		assertTrue(postFilResponseN6.body != null)
		assertEquals(Mimetype.applicationSlashPdf, postFilResponseN6.body!!.mimetype)
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
		val token = getToken()

		val soknadDto = opprettEnSoknad(token, skjemanr, spraak, vedlegg)

		val vedleggN6 = soknadDto.vedleggsListe.first { it.vedleggsnr == "N6" }
		assertEquals(OpplastingsStatusDto.ikkeValgt, vedleggN6.opplastingsStatus)

		val multipart = LinkedMultiValueMap<Any, Any>()
		multipart.add("file", ClassPathResource("/NAV 54-editert.pdf"))

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

	@Test
	fun sjekkAtOpplastingAvKryptertFilGirFeilTest() {
		val skjemanr = defaultSkjemanr
		val spraak = "nb_NO"
		val vedlegg = listOf("N6", "W2")
		val token = getToken()

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

	@Test
	fun sjekkAtOpplastingAvUlovligFilformatGirFeilTest() {
		val skjemanr = defaultSkjemanr
		val spraak = "nb_NO"
		val vedlegg = listOf("N6", "W2")
		val token = getToken()

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

	private fun getToken(): String {
		return mockOAuth2Server.issueToken(
			tokenx,
			MockLoginController::class.java.simpleName,
			DefaultOAuth2TokenCallback(
				tokenx,
				subject,
				JOSEObjectType.JWT.type,
				listOf(audience),
				mapOf("acr" to "Level4"),
				expiry.toLong()
			)
		).serialize()
	}

	@Test
	fun sjekkAtKorrektListeAvSokersAktiveSoknaderHentesTest() {
		val token: String = getToken()

		// Initiell liste
		val soknaderInitRespons = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad",
			HttpMethod.GET,
			HttpEntity<Unit>(Hjelpemetoder.createHeaders(token)),
			object : ParameterizedTypeReference<List<DokumentSoknadDto>>() {}
		)
		kotlin.test.assertTrue(soknaderInitRespons.statusCode == HttpStatus.OK && soknaderInitRespons.body != null)
		val hentetInitListe = soknaderInitRespons.body

		val forventetListe = LinkedList<DokumentSoknadDto>()

		forventetListe.add(opprettEnSoknad(token = token, skjemanr = defaultSkjemanr, spraak = "nb_NO", listOf("X2")))
		forventetListe.add(opprettEnSoknad(token = token, skjemanr = "NAV 10-07.17", spraak = "nn_NO", listOf("X2", "M3")))

		if (hentetInitListe != null) {
			forventetListe.addAll(hentetInitListe)
		}

		// Test endepunkt for å hente opprettede aktive søknader
		val soknaderRespons = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad",
			HttpMethod.GET,
			HttpEntity<Unit>(Hjelpemetoder.createHeaders(token)),
			object : ParameterizedTypeReference<List<DokumentSoknadDto>>() {}
		)
		kotlin.test.assertTrue(soknaderRespons.statusCode == HttpStatus.OK && soknaderRespons.body != null)
		val hentetListe = soknaderRespons.body
		assertTrue(hentetListe != null)
		assertTrue(forventetListe.size == hentetListe!!.size)

	}


	@Test
	fun oppdrettVedleggTest() {
		val skjemanr = defaultSkjemanr
		val spraak = "nb_NO"
		val vedlegg = emptyList<String>()

		val token: String = mockOAuth2Server.issueToken(
			tokenx,
			MockLoginController::class.java.simpleName,
			DefaultOAuth2TokenCallback(
				tokenx,
				subject,
				JOSEObjectType.JWT.type,
				listOf(audience),
				mapOf("acr" to "Level4"),
				expiry.toLong()
			)
		).serialize()

		val opprettSoknadBody = OpprettSoknadBody(skjemanr, spraak, vedlegg)
		val postRequestEntity = HttpEntity(opprettSoknadBody, Hjelpemetoder.createHeaders(token))

		val postResponse = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad", HttpMethod.POST,
			postRequestEntity, DokumentSoknadDto::class.java
		)

		assertTrue(postResponse.body != null)
		val opprettetSoknadDto = postResponse.body
		assertTrue(opprettetSoknadDto!!.vedleggsListe.isNotEmpty())

		val postVedleggDto = PostVedleggDto("Nytt vedlegg")
		val postVedleggRequestEntity = HttpEntity(postVedleggDto, Hjelpemetoder.createHeaders(token))
		val postVedleggResponse = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad/${opprettetSoknadDto.innsendingsId}/vedlegg", HttpMethod.POST,
			postVedleggRequestEntity, VedleggDto::class.java
		)

		assertTrue(postVedleggResponse.body != null)
		val nyttVedleggDto = postVedleggResponse.body
		assertTrue(nyttVedleggDto != null)
		assertEquals("Nytt vedlegg", nyttVedleggDto!!.tittel)
	}

	@Test
	fun `Skal fungere med nye idporten acr claim (idporten-loa-high)`() {
		// Gitt
		val skjemanr = defaultSkjemanr
		val spraak = "nb_NO"

		val token: String = mockOAuth2Server.issueToken(
			tokenx,
			MockLoginController::class.java.simpleName,
			DefaultOAuth2TokenCallback(
				tokenx,
				subject,
				JOSEObjectType.JWT.type,
				listOf(audience),
				mapOf("acr" to "idporten-loa-high"),
				expiry.toLong()
			)
		).serialize()

		val opprettSoknadBody = OpprettSoknadBody(skjemanr, spraak)
		val requestEntity = HttpEntity(opprettSoknadBody, Hjelpemetoder.createHeaders(token))

		// Når
		val response = restTemplate.exchange(
			"http://localhost:${serverPort}/frontend/v1/soknad", HttpMethod.POST,
			requestEntity, DokumentSoknadDto::class.java
		)

		// Så
		assertEquals(HttpStatus.CREATED, response.statusCode)
		assertTrue(response.body != null)

	}


	@Test
	internal fun testResult() {
		val hentPerson = Person(listOf(Navn("Fornavn", "Mellomnavn", "Etternavn")))
		val hentIdenter = Identliste(
			listOf(
				IdentInformasjon(defaultUser, IdentGruppe.FOLKEREGISTERIDENT, false),
				IdentInformasjon("12345678902", IdentGruppe.FOLKEREGISTERIDENT, true)
			)
		)
		val gson = Gson()
		val personJson: String = gson.toJson(hentPerson)
		println(personJson)
		val identJson = gson.toJson(hentIdenter)
		println(identJson)
	}


}
