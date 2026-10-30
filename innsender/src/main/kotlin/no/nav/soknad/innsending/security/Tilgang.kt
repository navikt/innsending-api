package no.nav.soknad.innsending.security

import org.springframework.security.access.prepost.PreAuthorize

/**
 * Tilgangene et gyldig innkommende token gir, avledet av issuer og claims (#461).
 *
 * Reglene er de samme som `@ProtectedWithClaims` i token-support håndhever i dag. En claim med strengverdi må være lik
 * verdien i regelen, og en claim med listeverdi må inneholde den. Avledningen brukes både når token-support har validert
 * tokenet (broen i steg 3a–3h) og når Texas har validert det (steg 3i og 3j), slik at `@PreAuthorize` ikke endres i byttet.
 */
object Tilgang {
	const val TOKENX = "UTSTEDER_TOKENX"
	const val AZURE = "UTSTEDER_AZURE"

	/** Innbygger med TokenX-token og høyt sikkerhetsnivå: `acr=Level4` eller `acr=idporten-loa-high`. */
	const val INNBYGGER = "INNBYGGER_HOYT_NIVAA"

	/** Azure-token med nøyaktig `scp=admin-access defaultaccess`. Rekkefølge og ekstra scope gir ikke admin, som i dag. */
	const val AZURE_ADMIN = "AZURE_ADMIN"

	/** Azure-token der `roles` inneholder `nologin-access`. */
	const val AZURE_NOLOGIN = "AZURE_NOLOGIN"

	private val hoyeAcrVerdier = setOf("Level4", "idporten-loa-high")
	private const val ADMIN_SCP = "admin-access defaultaccess"
	private const val NOLOGIN_ROLLE = "nologin-access"

	fun tilgangerFor(utsteder: TokenUtsteder, claims: Map<String, Any?>): Set<String> = when (utsteder) {
		TokenUtsteder.TOKENX -> buildSet {
			add(TOKENX)
			if (hoyeAcrVerdier.any { harClaim(claims, "acr", it) }) add(INNBYGGER)
		}

		TokenUtsteder.AZURE -> buildSet {
			add(AZURE)
			if (harClaim(claims, "scp", ADMIN_SCP)) add(AZURE_ADMIN)
			if (harClaim(claims, "roles", NOLOGIN_ROLLE)) add(AZURE_NOLOGIN)
		}
	}

	// Samme semantikk som JwtTokenClaims.containsClaim i token-support
	private fun harClaim(claims: Map<String, Any?>, navn: String, verdi: String): Boolean =
		when (val claim = claims[navn]) {
			is String -> claim == verdi
			is Collection<*> -> claim.contains(verdi)
			else -> false
		}
}

/** Innbygger med TokenX-token og høyt sikkerhetsnivå. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("hasAuthority('" + Tilgang.INNBYGGER + "')")
annotation class KreverInnbygger

/** Gyldig Azure-token, uten krav til scope eller rolle. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("hasAuthority('" + Tilgang.AZURE + "')")
annotation class KreverAzure

/** Azure-token fra innsending-admin med admin-scope. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("hasAuthority('" + Tilgang.AZURE_ADMIN + "')")
annotation class KreverAzureAdmin

/** Azure-token med rollen nologin-access. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("hasAuthority('" + Tilgang.AZURE_NOLOGIN + "')")
annotation class KreverAzureNologin
