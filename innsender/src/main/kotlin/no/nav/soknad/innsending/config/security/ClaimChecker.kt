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

	fun hasAccess(authentication: Authentication, isAzureIssuer: Boolean =false,  requiredClaims: Collection<String>, allRequired: Boolean = true): Boolean {
		if (authentication !is JwtAuthenticationToken) {
			log.warn("Ugyldig authentication-type: ${authentication::class.java.simpleName}")
			return false
		}

		val jwt = authentication.token
		val issuer = jwt.issuer?.toString() ?: return false
		if (isAzureIssuer && !issuer.equals(azureadIssuer, ignoreCase = true)) {
			log.info("Avvist: issuer $issuer er ikke konfigurert AzureAD issuer")
			return false
		}
		if (!isAzureIssuer && !issuer.equals(tokenxIssuer, ignoreCase = true)) {
			log.info("Avvist: issuer $issuer er ikke konfigurert TokenX issuer")
			return false
		}

		if (requiredClaims.isEmpty()) return true

		var oneOk = false
		for (claim in requiredClaims) {
			val claimName = claim.substringBefore("=")
			val requiredValue = claim.substringAfter("=")
			if (!checkOneClaim(jwt, claimName, requiredValue)) {
				log.info("Mangler claim $claimName=$requiredValue")
				if (allRequired) {
					return false
				}
			} else {
				oneOk = true
			}
		}

		return oneOk
/*

		val requiredRoles = requiredClaims.filter{it.contains("roles=")}.map { it.substringAfter("roles=") }.map { it.split(" ") }.flatten()
		if (!requiredRoles.isEmpty()) {
			val roles = jwt
				.getClaimAsStringList("roles")
				?: emptyList()
			val harAlleRoller = requiredRoles.all { requiredRole -> roles.contains(requiredRole) }
			if (!harAlleRoller) {
				log.info("Avvist: mangler ett eller flere påkrevde roller. Har: $roles, krever: $requiredRoles")
				return false
			}
		}

		val requiredScopes: List<String> =
			when (val claim = requiredClaims.find { it.contains("scp=") || it.contains("scope=") }) {
				is String -> if (claim.substringAfter("=").contains(" "))
					claim.substringAfter("=").split(" ") else listOf(claim.substringAfter("="))
				else -> emptyList()
			}

		if (requiredScopes.isEmpty()) return true

		val scopes: List<String> =
			when (val claim = jwt.claims["scp"] ?: jwt.claims["scope"]) {
				is String -> claim.split(" ")
				is Collection<*> -> claim.filterIsInstance<String>()
				else -> emptyList()
			}
		val harAlleScopes = requiredScopes.all { scope -> scopes.contains(scope) }

		if (!harAlleScopes) {
			log.info("Avvist: mangler ett eller flere påkrevde scopes. Har: $scopes, krever: $requiredScopes")
		}

		return harAlleScopes
*/
	}

	private fun checkOneClaim(jwt: org.springframework.security.oauth2.jwt.Jwt, claimName: String, requiredValue: String): Boolean {
		val claimValue = jwt.claims[claimName]?.toString()
		if (claimValue == null) {
			log.info("Mangler påkrevd claim $claimName")
			return false
		}

		val requiredValues: List<String> = requiredValue.split(" ")
		val claims: List<String> = claimValue.split(" ")
		log.info("TMP: claimName=$claimName, claimValue=$claimValue, requiredValues=$requiredValues")
		return requiredValues.all { required -> claims.contains(required) }

	}

}

