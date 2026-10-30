package no.nav.soknad.innsending.security

import no.nav.soknad.innsending.security.Tilgang.AZURE
import no.nav.soknad.innsending.security.Tilgang.AZURE_ADMIN
import no.nav.soknad.innsending.security.Tilgang.AZURE_NOLOGIN
import no.nav.soknad.innsending.security.Tilgang.INNBYGGER
import no.nav.soknad.innsending.security.Tilgang.TOKENX
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

// Reglene er de samme som @ProtectedWithClaims i token-support håndhevet før migreringen (#461)
class TilgangTest {

	private fun tokenX(vararg claims: Pair<String, Any?>) = Tilgang.tilgangerFor(TokenUtsteder.TOKENX, mapOf(*claims))
	private fun azure(vararg claims: Pair<String, Any?>) = Tilgang.tilgangerFor(TokenUtsteder.AZURE, mapOf(*claims))

	@Test
	fun `TokenX med høyt nivå gir innbyggertilgang`() {
		assertEquals(setOf(TOKENX, INNBYGGER), tokenX("acr" to "Level4"))
		assertEquals(setOf(TOKENX, INNBYGGER), tokenX("acr" to "idporten-loa-high"))
	}

	@Test
	fun `TokenX uten høyt nivå gir ikke innbyggertilgang`() {
		assertEquals(setOf(TOKENX), tokenX("acr" to "idporten-loa-substantial"))
		assertEquals(setOf(TOKENX), tokenX("acr" to "Level3"))
		assertEquals(setOf(TOKENX), tokenX())
		assertEquals(setOf(TOKENX), tokenX("acr" to null))
		// Store og små bokstaver teller, som i token-support
		assertEquals(setOf(TOKENX), tokenX("acr" to "level4"))
	}

	@Test
	fun `TokenX-claims gir aldri Azure-tilganger`() {
		assertEquals(
			setOf(TOKENX, INNBYGGER),
			tokenX("acr" to "Level4", "scp" to "admin-access defaultaccess", "roles" to listOf("nologin-access")),
		)
	}

	@Test
	fun `Azure gir admin bare med nøyaktig scope-streng`() {
		assertEquals(setOf(AZURE, AZURE_ADMIN), azure("scp" to "admin-access defaultaccess"))
		assertEquals(setOf(AZURE), azure("scp" to "defaultaccess admin-access"))
		assertEquals(setOf(AZURE), azure("scp" to "admin-access defaultaccess annet"))
		assertEquals(setOf(AZURE), azure("scp" to "defaultaccess"))
		assertEquals(setOf(AZURE), azure())
	}

	@Test
	fun `Azure gir nologin når roles inneholder rollen`() {
		assertEquals(setOf(AZURE, AZURE_NOLOGIN), azure("roles" to listOf("nologin-access")))
		assertEquals(setOf(AZURE, AZURE_NOLOGIN), azure("roles" to listOf("annen", "nologin-access")))
		assertEquals(setOf(AZURE, AZURE_NOLOGIN), azure("roles" to "nologin-access"))
		assertEquals(setOf(AZURE), azure("roles" to listOf("annen")))
		assertEquals(setOf(AZURE), azure("roles" to emptyList<String>()))
	}

	@Test
	fun `Azure-claims gir aldri innbyggertilgang`() {
		assertEquals(setOf(AZURE), azure("acr" to "Level4"))
	}
}
