package no.nav.soknad.innsending.security

import org.springframework.security.access.prepost.PreAuthorize

/**
 * Tilgangene et gyldig innkommende token gir, avledet av issuer og claims (#461).
 *
 * Reglene er de samme som `@ProtectedWithClaims` i token-support håndhever i dag. En claim med strengverdi må være lik
 * verdien i regelen, og en claim med listeverdi må inneholde den. Avledningen brukes både når token-support har validert
 * tokenet (broen i steg 3a–3h) og når Texas har validert det (steg 3i og 3j), slik at `@PreAuthorize` ikke endres i byttet.
 */
object Access {
	const val TOKENX = "ISSUER_TOKENX"
	const val AZURE = "ISSUER_AZURE"

	/**
	 * Innbygger med TokenX-token og høyt sikkerhetsnivå: `acr=Level4` eller `acr=idporten-loa-high`.
	 * Tokenet må også ha pid eller sub. Texas krever ikke sub, så uten denne regelen ville et token uten begge nådd
	 * controlleren og gitt 401 i stedet for 403, slik token-support ga (#461, steg 3j). En pid med feil type gir
	 * fortsatt 401 fra identitetsoppslaget, som før.
	 */
	const val CITIZEN = "CITIZEN_HIGH_LOA"

	/** Azure-token med nøyaktig `scp=admin-access defaultaccess`. Rekkefølge og ekstra scope gir ikke admin, som i dag. */
	const val AZURE_ADMIN = "AZURE_ADMIN"

	/** Azure-token der `roles` inneholder `nologin-access`. */
	const val AZURE_NOLOGIN = "AZURE_NOLOGIN"

	private val highAcrValues = setOf("Level4", "idporten-loa-high")
	private const val ADMIN_SCP = "admin-access defaultaccess"
	private const val NOLOGIN_ROLE = "nologin-access"

	fun authoritiesFor(issuer: TokenIssuer, claims: Map<String, Any?>): Set<String> = when (issuer) {
		TokenIssuer.TOKENX -> buildSet {
			add(TOKENX)
			if (highAcrValues.any { hasClaim(claims, "acr", it) } && hasIdentity(claims)) add(CITIZEN)
		}

		TokenIssuer.AZURE -> buildSet {
			add(AZURE)
			if (hasClaim(claims, "scp", ADMIN_SCP)) add(AZURE_ADMIN)
			if (hasClaim(claims, "roles", NOLOGIN_ROLE)) add(AZURE_NOLOGIN)
		}
	}

	private fun hasIdentity(claims: Map<String, Any?>): Boolean = claims["pid"] != null || claims["sub"] is String

	// Samme semantikk som JwtTokenClaims.containsClaim i token-support
	private fun hasClaim(claims: Map<String, Any?>, name: String, value: String): Boolean =
		when (val claim = claims[name]) {
			is String -> claim == value
			is Collection<*> -> claim.contains(value)
			else -> false
		}
}

/** Innbygger med TokenX-token og høyt sikkerhetsnivå. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("hasAuthority('" + Access.CITIZEN + "')")
annotation class RequiresCitizen

/** Gyldig Azure-token, uten krav til scope eller rolle. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("hasAuthority('" + Access.AZURE + "')")
annotation class RequiresAzure

/** Azure-token fra innsending-admin med admin-scope. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("hasAuthority('" + Access.AZURE_ADMIN + "')")
annotation class RequiresAzureAdmin

/** Azure-token med rollen nologin-access. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("hasAuthority('" + Access.AZURE_NOLOGIN + "')")
annotation class RequiresAzureNologin
