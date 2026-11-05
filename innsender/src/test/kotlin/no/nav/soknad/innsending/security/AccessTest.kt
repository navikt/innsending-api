package no.nav.soknad.innsending.security

import no.nav.soknad.innsending.security.Access.AZURE
import no.nav.soknad.innsending.security.Access.AZURE_ADMIN
import no.nav.soknad.innsending.security.Access.AZURE_NOLOGIN
import no.nav.soknad.innsending.security.Access.CITIZEN
import no.nav.soknad.innsending.security.Access.TOKENX
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

// Reglene skal være de samme som @ProtectedWithClaims i token-support (#461)
class AccessTest {

	private fun tokenX(vararg claims: Pair<String, Any?>) = Access.authoritiesFor(TokenIssuer.TOKENX, mapOf(*claims))
	private fun azure(vararg claims: Pair<String, Any?>) = Access.authoritiesFor(TokenIssuer.AZURE, mapOf(*claims))

	@Test
	fun `TokenX with a high level gives citizen access`() {
		assertEquals(setOf(TOKENX, CITIZEN), tokenX("acr" to "Level4"))
		assertEquals(setOf(TOKENX, CITIZEN), tokenX("acr" to "idporten-loa-high"))
	}

	@Test
	fun `TokenX without a high level gives no citizen access`() {
		assertEquals(setOf(TOKENX), tokenX("acr" to "idporten-loa-substantial"))
		assertEquals(setOf(TOKENX), tokenX("acr" to "Level3"))
		assertEquals(setOf(TOKENX), tokenX())
		assertEquals(setOf(TOKENX), tokenX("acr" to null))
		// Store og små bokstaver teller, som i token-support
		assertEquals(setOf(TOKENX), tokenX("acr" to "level4"))
	}

	@Test
	fun `TokenX claims never give Azure authorities`() {
		assertEquals(
			setOf(TOKENX, CITIZEN),
			tokenX("acr" to "Level4", "scp" to "admin-access defaultaccess", "roles" to listOf("nologin-access")),
		)
	}

	@Test
	fun `Azure gives admin only with the exact scope string`() {
		assertEquals(setOf(AZURE, AZURE_ADMIN), azure("scp" to "admin-access defaultaccess"))
		assertEquals(setOf(AZURE), azure("scp" to "defaultaccess admin-access"))
		assertEquals(setOf(AZURE), azure("scp" to "admin-access defaultaccess annet"))
		assertEquals(setOf(AZURE), azure("scp" to "defaultaccess"))
		assertEquals(setOf(AZURE), azure())
	}

	@Test
	fun `Azure gives nologin when roles contains the role`() {
		assertEquals(setOf(AZURE, AZURE_NOLOGIN), azure("roles" to listOf("nologin-access")))
		assertEquals(setOf(AZURE, AZURE_NOLOGIN), azure("roles" to listOf("annen", "nologin-access")))
		assertEquals(setOf(AZURE, AZURE_NOLOGIN), azure("roles" to "nologin-access"))
		assertEquals(setOf(AZURE), azure("roles" to listOf("annen")))
		assertEquals(setOf(AZURE), azure("roles" to emptyList<String>()))
	}

	@Test
	fun `Azure claims never give citizen access`() {
		assertEquals(setOf(AZURE), azure("acr" to "Level4"))
	}
}
