package no.nav.soknad.innsending.consumerapis.texas

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.net.SocketTimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TexasIntrospeksjonKlientTest {

	private val endpoint = "http://texas.test/api/v1/introspect"
	private val token = "eyJhbGciOiJSUzI1NiJ9.innkommende-token-med-pid.signatur"

	private lateinit var server: MockRestServiceServer
	private lateinit var klient: TexasIntrospeksjonKlient

	@BeforeEach
	fun setUp() {
		val builder = RestClient.builder()
		server = MockRestServiceServer.bindTo(builder).build()
		klient = TexasIntrospeksjonKlient(builder.build(), endpoint)
	}

	@Test
	fun `gyldig token gir claimene uten active`() {
		server.expect(requestTo(endpoint))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().json("""{"identity_provider":"tokenx","token":"$token"}""", true))
			.andRespond(withSuccess("""{"active":true,"pid":"12345678901","acr":"idporten-loa-high"}""", MediaType.APPLICATION_JSON))

		val svar = klient.introspekter(TexasIntrospeksjonKlient.TOKENX, token)

		assertTrue(svar.aktiv)
		assertEquals(mapOf("pid" to "12345678901", "acr" to "idporten-loa-high"), svar.claims)
		server.verify()
	}

	@Test
	fun `ugyldig token gir verken claims eller feilteksten fra Texas`() {
		server.expect(requestTo(endpoint))
			.andRespond(withSuccess("""{"active":false,"error":"ugyldig token $token for 12345678901"}""", MediaType.APPLICATION_JSON))

		val svar = klient.introspekter(TexasIntrospeksjonKlient.ENTRA_ID, token)

		assertFalse(svar.aktiv)
		assertTrue(svar.claims.isEmpty())
		assertFalse(svar.toString().contains(token))
		assertFalse(svar.toString().contains("12345678901"))
	}

	@Test
	fun `feilstatus fra Texas gir TexasUnavailableException uten token og body`() {
		server.expect(requestTo(endpoint))
			.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("intern feil med $token"))

		val feil = assertFailsWith<TexasUnavailableException> { klient.introspekter(TexasIntrospeksjonKlient.TOKENX, token) }

		assertEquals("Texas svarte 500 på introspeksjon for identity_provider=tokenx", feil.message)
		assertNull(feil.cause)
	}

	@Test
	fun `tidsavbrudd gir TexasUnavailableException uten cause`() {
		server.expect(requestTo(endpoint)).andRespond(withException(SocketTimeoutException("Read timed out")))

		val feil = assertFailsWith<TexasUnavailableException> { klient.introspekter(TexasIntrospeksjonKlient.TOKENX, token) }

		assertFalse(feil.message!!.contains(token))
		assertNull(feil.cause)
	}

	@Test
	fun `svar uten active er en feil`() {
		server.expect(requestTo(endpoint)).andRespond(withSuccess("""{"pid":"12345678901"}""", MediaType.APPLICATION_JSON))

		assertFailsWith<TexasUnavailableException> { klient.introspekter(TexasIntrospeksjonKlient.TOKENX, token) }
	}

	@Test
	fun `toString viser ikke claims`() {
		assertFalse(TexasIntrospeksjon(true, mapOf("pid" to "12345678901")).toString().contains("12345678901"))
	}
}
