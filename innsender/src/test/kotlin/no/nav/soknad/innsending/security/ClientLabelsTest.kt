package no.nav.soknad.innsending.security

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ClientLabelsTest {

	private fun token(vararg claims: Pair<String, Any?>) = ValidatedToken(mapOf(*claims), "t")

	@Test
	fun `Azure uses azp_name and TokenX uses client_id`() {
		val labels = ClientLabels()

		assertEquals("prod-gcp:flex:app", labels.of(token("azp_name" to "prod-gcp:flex:app", "azp" to "guid"), TokenIssuer.AZURE))
		assertEquals("prod-gcp:team:app", labels.of(token("client_id" to "prod-gcp:team:app", "azp" to "annen"), TokenIssuer.TOKENX))
	}

	@Test
	fun `azp is used when the preferred claim is missing`() {
		assertEquals("klient", ClientLabels().of(token("azp" to "klient"), TokenIssuer.AZURE))
	}

	@Test
	fun `missing, non-string, long or unexpected values give unknown`() {
		val labels = ClientLabels()

		listOf(
			token(),
			token("azp_name" to listOf("a")),
			token("azp_name" to "a".repeat(121)),
			token("azp_name" to "app\nfalsk linje"),
			token("azp_name" to "app name"),
			token("azp_name" to ""),
		).forEach { assertEquals("unknown", labels.of(it, TokenIssuer.AZURE)) }
	}

	@Test
	fun `values beyond the limit give other, while known values are kept`() {
		val labels = ClientLabels(maxClients = 2)

		assertEquals("a", labels.of(token("azp_name" to "a"), TokenIssuer.AZURE))
		assertEquals("b", labels.of(token("azp_name" to "b"), TokenIssuer.AZURE))
		assertEquals("other", labels.of(token("azp_name" to "c"), TokenIssuer.AZURE))
		assertEquals("a", labels.of(token("azp_name" to "a"), TokenIssuer.AZURE))
	}

	@Test
	fun `the log text contains only the labels`() {
		assertEquals(
			"result=inactive, issuer=azure, client=unknown",
			TokenValidationOutcome(TokenValidationResult.INACTIVE, "azure", "unknown").toString(),
		)
	}
}
