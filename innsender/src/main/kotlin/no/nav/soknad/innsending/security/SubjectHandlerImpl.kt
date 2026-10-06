package no.nav.soknad.innsending.security

import no.nav.soknad.innsending.exceptions.BackendErrorException
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

@Component
@Profile("test | dev | prod")
class SubjectHandlerImpl: SubjectHandlerInterface {

	private val log = LoggerFactory.getLogger(SubjectHandlerImpl::class.java)

	companion object {
		private const val CLAIM_PID = "pid"
		private const val CLIENT_ID = "client_id"
		private const val AZP = "azp"
	}

	override fun getConsumerId(): String {
		return "srvinnsending-api"
	}

	override fun getUserIdFromToken(): String {
		return getPid() ?: getSubject() ?: throw RuntimeException("Could not find any userId for token in pid or sub claim")
	}

	override fun getToken(): String {
		return currentJwt()?.tokenValue ?: throw BackendErrorException("Could not get token")
	}

	// The MockOAuth2Server sets AZP claim for the client id, while the real tokens uses client_id claim
	override fun getClientId(): String {
		val jwt = currentJwt()
		return jwt?.claims?.get(CLIENT_ID) as? String
			?: jwt?.claims?.get(AZP) as? String
			?: throw RuntimeException("Could not find any clientId for token in client_id or azp claim")
	}

	override fun getNavIdent(): String {
		val jwt = currentJwt()
		return jwt?.claims?.get("NAVident") as? String
			?: throw RuntimeException("Could not find NAVident claim in token")
	}

	fun currentJwt(): Jwt? {
		val auth = SecurityContextHolder.getContext().authentication
		return (auth as? JwtAuthenticationToken)?.token
	}

	fun getSubject(): String? =
		currentJwt()?.subject

	fun getEmail(): String? =
		currentJwt()?.claims?.get("preferred_username") as? String

	fun getPid(): String? =
		currentJwt()?.claims?.get("pid") as? String

	fun getIssuer(): String? =
		currentJwt()?.claims?.get("iss") as? String

	fun getClaim(claimName: String): Any? =
		currentJwt()?.claims?.get(claimName)

	fun isLoggedIn(isInternal: Boolean): Boolean =
		if (getPid() != null)
			true
		else
			false

	fun logClaims() {
		val jwt = currentJwt()
		if (jwt == null) {
			log.info("No JWT in security context")
		} else {
			log.info("JWT issuer=${jwt.issuer}, subject=${jwt.subject}, claims=${jwt.claims.keys}")
		}
	}
}


