package no.nav.soknad.innsending.config.security

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component


@Component("claimChecker")
class ClaimChecker(
	@Value("\${auth.issuers.azuread.issuer-uri}") private val azureadIssuer: String,
	@Value("\${auth.issuers.tokenx.issuer-uri}") private val tokenxIssuer: String
) {

	private val log = LoggerFactory.getLogger(javaClass)

	fun hasAccess(authentication: Authentication, isAzureIssuer: Boolean =false,  requiredClaims: Collection<String>): Boolean {
		if (authentication !is JwtAuthenticationToken) {
			log.warn("Ugyldig authentication-type: ${authentication::class.java.simpleName}")
			return false
		}

		val jwt = authentication.token
		val roles = jwt
			.getClaimAsStringList("roles")
			?: emptyList()
		val harAlleRoller = requiredClaims.filter{it.contains("roles=")}.map { it.substringAfter("roles=") }
			.all { role -> roles.contains(role) }
		if (!harAlleRoller) {
			log.info("Avvist: mangler ett eller flere påkrevde roller. Har: $roles, krever: $requiredClaims")
			return false
		}

		val issuer = jwt.issuer?.toString() ?: return false
		if (isAzureIssuer && !issuer.equals(azureadIssuer, ignoreCase = true)) {
			log.info("Avvist: issuer $issuer er ikke konfigurert AzureAD issuer")
			return false
		}
		if (!isAzureIssuer && !issuer.equals(tokenxIssuer, ignoreCase = true)) {
			log.info("Avvist: issuer $issuer er ikke konfigurert TokenX issuer")
			return false
		}

		val scopes: List<String> =
			when (val claim = jwt.claims["scp"] ?: jwt.claims["scope"]) {
				is String -> claim.split(" ")
				is Collection<*> -> claim.filterIsInstance<String>()
				else -> emptyList()
			}
		val harAlleScopes = requiredClaims.filter{it.contains("scope")}.map { it.substringAfter("scope=") }
			.all { scope -> scopes.contains(scope) }

		if (!harAlleScopes) {
			log.info("Avvist: mangler ett eller flere påkrevde scopes. Har: $scopes, krever: $requiredClaims")
		}

		return harAlleScopes
	}

}

