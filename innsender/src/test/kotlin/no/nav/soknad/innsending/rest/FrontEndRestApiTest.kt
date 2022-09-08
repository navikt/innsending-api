package no.nav.soknad.innsending.rest

import com.google.gson.Gson
import com.nimbusds.jose.JOSEObjectType
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.security.token.support.spring.test.MockLoginController
import no.nav.soknad.innsending.InnsendingApiApplication
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.model.OpprettSoknadBody
import no.nav.soknad.innsending.pdl.generated.enums.IdentGruppe
import no.nav.soknad.innsending.pdl.generated.hentpersoninfo.IdentInformasjon
import no.nav.soknad.innsending.pdl.generated.hentpersoninfo.Identliste
import no.nav.soknad.innsending.pdl.generated.hentpersoninfo.Navn
import no.nav.soknad.innsending.pdl.generated.hentpersoninfo.Person
import no.nav.soknad.innsending.utils.createHeaders
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.junit.jupiter.api.Assertions.*

@Suppress("DEPRECATION")
@ActiveProfiles("test")
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
	properties = ["spring.main.allow-bean-definition-overriding=true"],
	classes = [InnsendingApiApplication::class])
@ExtendWith(SpringExtension::class)
@AutoConfigureWireMock
@EnableMockOAuth2Server(port = 9898)
class FrontEndRestApiTest {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var restTemplate: TestRestTemplate


	@Value("\${server.port}")
	var serverPort: Int? = 9064

	private val tokenx = "tokenx"
	private val subject = "12345678901"
	private val audience = "aud-localhost"
	private val expiry = 2*3600
	private val defaultUser = "12345678901"


	@Test
	fun opprettSoknadTest() {
		val skjemanr = "NAV 95-00.11"
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
		val requestEntity =	HttpEntity(opprettSoknadBody, createHeaders(token))

		val response = restTemplate.exchange("http://localhost:${serverPort}/frontend/v1/soknad", HttpMethod.POST,
			requestEntity, DokumentSoknadDto::class.java
		)

		assertTrue(response.body != null)

	}

	@Test
	fun hentOpprettetSoknadTest() {
		val skjemanr = "NAV 95-00.11"
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
		val postRequestEntity =	HttpEntity(opprettSoknadBody, createHeaders(token))

		val postResponse = restTemplate.exchange("http://localhost:${serverPort}/frontend/v1/soknad", HttpMethod.POST,
			postRequestEntity, DokumentSoknadDto::class.java
		)

		assertTrue(postResponse.body != null)

		val opprettetSoknadDto = postResponse.body
		val getRequestEntity = HttpEntity<Unit>(createHeaders(token))
		val getResponse = restTemplate.exchange("http://localhost:${serverPort}/frontend/v1/soknad/${opprettetSoknadDto!!.innsendingsId}", HttpMethod.GET,
			getRequestEntity, DokumentSoknadDto::class.java
		)

		assertTrue(getResponse.body != null)
		val getSoknadDto = getResponse.body
		assertEquals(opprettetSoknadDto.innsendingsId, getSoknadDto!!.innsendingsId)
	}

	@Test
	fun oppdaterVedleggTest() {
		val skjemanr = "NAV 95-00.11"
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
		val postRequestEntity =	HttpEntity(opprettSoknadBody, createHeaders(token))

		val postResponse = restTemplate.exchange("http://localhost:${serverPort}/frontend/v1/soknad", HttpMethod.POST,
			postRequestEntity, DokumentSoknadDto::class.java
		)

		assertTrue(postResponse.body != null)
		val opprettetSoknadDto = postResponse.body
		assertTrue(opprettetSoknadDto!!.vedleggsListe.isNotEmpty())

		val vedleggDto = opprettetSoknadDto.vedleggsListe.first { !it.erHoveddokument }
		val patchVedleggDto = PatchVedleggDto("Endret tittel", OpplastingsStatusDto.sendesAvAndre)
		val patchRequestEntity = HttpEntity(patchVedleggDto, createHeaders(token))
		val patchResponse = restTemplate.exchange("http://localhost:${serverPort}/frontend/v1/soknad/${opprettetSoknadDto.innsendingsId}/vedlegg/${vedleggDto.id}", HttpMethod.PATCH,
			patchRequestEntity, VedleggDto::class.java
		)

		assertTrue(patchResponse.body != null)
		val patchedVedleggDto = patchResponse.body
		assertEquals(vedleggDto.id, patchedVedleggDto!!.id)
		assertEquals("Endret tittel", patchedVedleggDto.tittel)
		assertEquals(OpplastingsStatusDto.sendesAvAndre, patchedVedleggDto.opplastingsStatus)
	}

	@Test
	fun oppdrettVedleggTest() {
		val skjemanr = "NAV 95-00.11"
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
		val postRequestEntity =	HttpEntity(opprettSoknadBody, createHeaders(token))

		val postResponse = restTemplate.exchange("http://localhost:${serverPort}/frontend/v1/soknad", HttpMethod.POST,
			postRequestEntity, DokumentSoknadDto::class.java
		)

		assertTrue(postResponse.body != null)
		val opprettetSoknadDto = postResponse.body
		assertTrue(opprettetSoknadDto!!.vedleggsListe.isNotEmpty())

		val postVedleggDto = PostVedleggDto("Nytt vedlegg")
		val postVedleggRequestEntity = HttpEntity(postVedleggDto, createHeaders(token))
		val postVedleggResponse = restTemplate.exchange("http://localhost:${serverPort}/frontend/v1/soknad/${opprettetSoknadDto.innsendingsId}/vedlegg", HttpMethod.POST,
			postVedleggRequestEntity, VedleggDto::class.java
		)

		assertTrue(postVedleggResponse.body != null)
		val nyttVedleggDto = postVedleggResponse.body
		assertTrue(nyttVedleggDto != null)
		assertEquals("Nytt vedlegg", nyttVedleggDto!!.tittel)
	}


	@Test
	internal fun testResult() {
		val hentPerson = Person(listOf(Navn("Fornavn", "Mellomnavn","Etternavn")))
		val hentIdenter = Identliste(listOf(IdentInformasjon(defaultUser, IdentGruppe.FOLKEREGISTERIDENT, false ), IdentInformasjon("12345678902", IdentGruppe.FOLKEREGISTERIDENT, true )))
		val gson = Gson()
		val personJson: String = gson.toJson(hentPerson)
		println(personJson)
		val identJson = gson.toJson(hentIdenter)
		println(identJson)
	}


}
