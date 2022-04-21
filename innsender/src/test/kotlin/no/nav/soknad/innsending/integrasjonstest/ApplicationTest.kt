package no.nav.soknad.innsending.integrasjonstest

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.http.ContentTypeHeader
import io.restassured.module.mockmvc.RestAssuredMockMvc
import com.github.tomakehurst.wiremock.client.WireMock.containing
/*
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.security.mock.oauth2.token.OAuth2TokenCallback
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
*/
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.data.ldap.AutoConfigureDataLdap
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureDataJpa
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestEntityManager
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.transaction.TestTransaction
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcConfigurer
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.util.List
import javax.servlet.Filter

import no.nav.soknad.innsending.InnsendingApiApplication
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.utils.getBytesFromFile

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = [InnsendingApiApplication::class])
@ExtendWith(
	SpringExtension::class
)
@AutoConfigureDataJpa
@AutoConfigureTestDatabase
@AutoConfigureTestEntityManager
@AutoConfigureDataLdap
@Transactional
//@EnableMockOAuth2Server
@AutoConfigureWireMock
class ApplicationTest(private val restConfig: RestConfig) {
	@Autowired
	private val webApplicationContext: WebApplicationContext? = null


	@Autowired
	lateinit var restTemplate: TestRestTemplate

/*
	@Autowired
	var mockOAuth2Server: MockOAuth2Server? = null
*/


	@BeforeEach
	fun setup() {
		TestTransaction.flagForCommit()
		TestTransaction.end()
		TestTransaction.start()
		val filterCollection: Collection<Filter> = webApplicationContext!!.getBeansOfType(
			Filter::class.java
		).values
		val filters: Array<Filter> = arrayOf(filterCollection.first()) //filterCollection.toTypedArray()
		val mockMvcConfigurer: MockMvcConfigurer = object : MockMvcConfigurer {
/*
			override fun afterConfigurerAdded(builder: ConfigurableMockMvcBuilder<*>) {
				builder.addFilters(*filters)
			}
*/
		}
		RestAssuredMockMvc.webAppContextSetup(webApplicationContext, mockMvcConfigurer)
		WireMock.stubFor(
			WireMock.post(WireMock.urlPathMatching(restConfig.sanityEndpoint))
				.willReturn(
					aResponse().withStatus(HttpStatus.CREATED.value())
						.withHeader(org.apache.http.HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
						.withBodyFile(getBytesFromFile("/sanity.json").toString())
				)
		)
		WireMock.stubFor(
			WireMock.post(WireMock.urlPathMatching(restConfig.filestorageEndpoint))
				.willReturn(
					aResponse().withStatus(HttpStatus.OK.value())
						.withHeader(org.apache.http.HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
						.withBody("hei")
				)
		)
		WireMock.stubFor(
			WireMock.post(WireMock.urlPathMatching(restConfig.soknadsMottakerEndpoint))
				.willReturn(
					aResponse().withStatus(HttpStatus.OK.value())
						.withHeader(org.apache.http.HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
						.withBody("ok")
				)
		)
/* TODO
		WireMock.stubFor(
			WireMock.get(WireMock.urlPathMatching(restConfig.pdlEndpoint))
				.willReturn(
					aResponse().withStatus(HttpStatus.OK.value())
						.withHeader(org.apache.http.HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
						.withBody("Oppgave endret")
				)
		)
		WireMock.stubFor(
			WireMock.get(WireMock.urlPathMatching("/OPPGAVE/[8]*"))
				.willReturn(
					WireMock.aResponse().withStatus(HttpStatus.OK.value())
						.withHeader(org.apache.http.HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
						.withBodyFile("oppgave/hentOppgaveIkkeEksisterendeJournalpostResponse.json")
				)
		)
		WireMock.stubFor(
			WireMock.get(WireMock.urlPathMatching("/OPPGAVE/[9]*"))
				.willReturn(
					WireMock.aResponse().withStatus(HttpStatus.OK.value())
						.withHeader(org.apache.http.HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
						.withBodyFile("oppgave/hentOppgaveIngenJournalpostSattResponse.json")
				)
		)
		WireMock.stubFor(
			WireMock.get(WireMock.urlPathMatching("/OPPGAVE/[0-7]*"))
				.willReturn(
					WireMock.aResponse().withStatus(HttpStatus.OK.value())
						.withHeader(org.apache.http.HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
						.withBodyFile("oppgave/hentOppgaveResponse.json")
				)
		)
		WireMock.stubFor(
			WireMock.get(WireMock.urlPathMatching("/STS"))
				.willReturn(
					aResponse().withStatus(HttpStatus.OK.value())
						.withHeader(org.apache.http.HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
						.withBody(
							String.format(
								"{\"accessToken\": \"%s\", \"token_type\": \"%s\", \"expires_in\":3600}",
								getToken(RESTSTS_ISSUER, no.nav.tilbakemeldingsmottak.itest.ApplicationTest.Companion.SRVUSER),
								"Bearer"
							)
						)
				)
		)
		WireMock.stubFor(
			WireMock.get(WireMock.urlPathMatching("/AKTOER/identer/"))
				.willReturn(
					WireMock.aResponse().withStatus(HttpStatus.OK.value())
						.withHeader(ContentTypeHeader.KEY, MediaType.APPLICATION_JSON_VALUE)
						.withBodyFile("aktoer/aktoerResponse.json")
				)
		)
		WireMock.stubFor(
			WireMock.get(WireMock.urlPathMatching("/norg2/enhet"))
				.willReturn(
					WireMock.aResponse().withStatus(HttpStatus.OK.value())
						.withHeader(ContentTypeHeader.KEY, MediaType.APPLICATION_JSON_VALUE)
						.withBody(createNorg2Response())
				)
		)

		WireMock.stubFor(
				WireMock.post(WireMock.urlPathMatching("/safgraphql"))
					.willReturn(
						WireMock.aResponse().withStatus(HttpStatus.OK.value())
							.withHeader(ContentTypeHeader.KEY, MediaType.APPLICATION_JSON_VALUE)
							.withBody(createSafGraphqlResponse())
					)
			)
*/
	}

/*
	@AfterEach
	fun tearDown() {
		WireMock.reset()
	}
*/

/*
	fun createHeaders(): HttpHeaders {
		val headers = HttpHeaders()
		headers.contentType = MediaType.APPLICATION_JSON
		headers.add(
			HttpHeaders.AUTHORIZATION,
			"Bearer " + getToken(no.nav.tilbakemeldingsmottak.itest.ApplicationTest.Companion.INNLOGGET_BRUKER)
		)
		return headers
	}
*/

/*
	fun createHeaders(issuer: String?, user: String?): HttpHeaders {
		val headers = HttpHeaders()
		headers.contentType = MediaType.APPLICATION_JSON
		headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + getToken(issuer, user))
		return headers
	}
*/

/*
	private fun getToken(user: String): String {
		return token(AZURE_ISSUER, user, no.nav.tilbakemeldingsmottak.itest.ApplicationTest.Companion.AUD)
	}
*/

/*
	fun getToken(issuer: String?, user: String?): String {
		return token(issuer, user, no.nav.tilbakemeldingsmottak.itest.ApplicationTest.Companion.AUD)
	}
*/

/*
	private fun token(issuerId: String?, subject: String?, audience: String): String {
		val oAuth2TokenCallback: OAuth2TokenCallback = DefaultOAuth2TokenCallback(
			issuerId,
			subject,
			List.of(audience), emptyMap(),
			3600
		)
		val loggedIn: Boolean = mockOAuth2Server.enqueueCallback(oAuth2TokenCallback)
		return mockOAuth2Server.issueToken(
			issuerId,
			"theclientid",
			oAuth2TokenCallback
		).serialize()
	}
*/

	companion object {
		protected const val CONSUMER_ID = "theclientid"
		private const val URL_SERVICEKLAGE = "/rest/serviceklage"
		private const val SRVUSER = "srvtilbakelendingse"
		private const val INNLOGGET_BRUKER = "14117119611"
		private const val AUD = "application"
	}
}
