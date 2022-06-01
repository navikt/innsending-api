package no.nav.soknad.innsending.consumerapis.saf

import com.nimbusds.jose.JOSEObjectType
import kotlinx.coroutines.runBlocking
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.security.token.support.spring.test.MockLoginController
import no.nav.soknad.innsending.dto.AktivSakDto
import no.nav.soknad.innsending.util.Constants.CORRELATION_ID
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.web.ServerProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.*
import java.util.List
import java.util.Map


@Suppress("DEPRECATION")
@ActiveProfiles("test")
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
	properties = ["spring.main.allow-bean-definition-overriding=true"])
@ExtendWith(SpringExtension::class)
@AutoConfigureWireMock
@EnableMockOAuth2Server // Tilgjengliggjør en oicd-provider for test. Se application-test.yml -> no.nav.security.jwt.issuer.selvbetjening for konfigurasjon
internal class SafAPITest {

	@Autowired
	lateinit var safAPI: SafAPI

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var restTemplate: TestRestTemplate

	@Value("\${server.port}")
	var serverPort: Int? = 9082

	@Autowired
	private val serverProperties: ServerProperties? = null

	@Test
	internal fun testHentJournalposter() {
		val tokenx = "tokenx"
		val subject = "12345678901"
		val audience = "dev-gcp:team-soknad:innsending-api"
		val expiry = 2*3600
		val token: String = mockOAuth2Server.issueToken(
			tokenx,
			MockLoginController::class.java.simpleName,
			DefaultOAuth2TokenCallback(
				tokenx,
				subject,
				JOSEObjectType.JWT.type,
				List.of(audience),
				Map.of("acr", "Level4"),
				if (expiry != null) expiry.toLong() else 3600
			)
		).serialize()

/*
		val requestEntity =	HttpEntity<Unit>(createHeaders(token))

	  val response = restTemplate.exchange("http://localhost:${serverPort}/innsendte/v1/hentAktiveSaker", HttpMethod.GET,
			requestEntity, object : ParameterizedTypeReference<List<AktivSakDto>>() {})
*/

/*
		val journalposter = safAPI.hentBrukersSakerIArkivet(subject)
		assertTrue(journalposter != null)
		runBlocking {
			val journalpostListe = safAPI.getSoknadsDataForPerson(subject)
			assertTrue(journalpostListe != null)
		}
*/
	}

	fun createHeaders(token: String): HttpHeaders {
		val headers = HttpHeaders()
		headers.contentType = MediaType.APPLICATION_JSON
		headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + token)
		headers.add(CORRELATION_ID, UUID.randomUUID().toString())
		return headers
	}

}
