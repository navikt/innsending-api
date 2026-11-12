package no.nav.soknad.innsending.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import no.nav.soknad.innsending.consumerapis.token.TokenServiceUnavailableException
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.util.SimpleMethodInvocation
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sjekker `@PreAuthorize` før Spring leser og validerer request-bodyen (#461).
 *
 * Metodesikkerheten i Spring Security slår først til når controller-metoden kalles, etter at argumentene er bundet.
 * Uten denne sjekken ville et kall uten gyldig token mot et endepunkt med body fått 400 eller 500 fra valideringen
 * i stedet for 403, og lært noe om endepunktet. Token-support sjekker på samme sted, i en interceptor.
 * Avvisning gir [AuthorizationDeniedException], som RestExceptionHandler gjør om til 403.
 *
 * For endepunkter med en regel teller interceptoren utfallet med [recorder] og legger det i
 * [TokenValidationOutcome.ATTRIBUTE], slik at 403-loggen viser årsaken. Feil i tellingen endrer aldri utfallet.
 * Endepunkter uten regel telles ikke.
 */
class AccessInterceptor(
	private val recorder: TokenValidationRecorder,
	private val clientLabels: ClientLabels = ClientLabels(),
) : HandlerInterceptor {

	private val preAuthorize = PreAuthorizeAuthorizationManager()
	private val logger = LoggerFactory.getLogger(javaClass)
	private val recordFailureLogged = AtomicBoolean(false)

	override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
		if (handler !is HandlerMethod) return true

		// Ingen @PreAuthorize gir null: offentlig endepunkt
		val decision = preAuthorize.authorize(::authentication, SimpleMethodInvocation(handler.bean, handler.method))
			?: return true

		record(request, decision.isGranted)

		if (decision.isGranted) return true
		// Texas svarte ikke, så vi vet ikke om tokenet er gyldig. Det skal gi 503, aldri 403 (#461)
		if (request.getAttribute(TokenAuthenticationFilter.TOKEN_VALIDATION_UNAVAILABLE) == true) {
			throw TokenServiceUnavailableException("Texas svarte ikke ved validering av innkommende token")
		}
		throw AuthorizationDeniedException("Access Denied", decision)
	}

	private fun record(request: HttpServletRequest, granted: Boolean) {
		try {
			val outcome = outcome(request, granted)
			request.setAttribute(TokenValidationOutcome.ATTRIBUTE, outcome)
			recorder.record(outcome)
		} catch (e: RuntimeException) {
			// Logges én gang, så en feil her ikke fyller loggen
			if (recordFailureLogged.compareAndSet(false, true)) {
				logger.warn("Kunne ikke registrere utfall av tokenvalidering (${e.javaClass.simpleName})")
			}
		}
	}

	private fun outcome(request: HttpServletRequest, granted: Boolean): TokenValidationOutcome {
		val authentication = SecurityContextHolder.getContext().authentication
		val grantedOrForbidden = if (granted) TokenValidationResult.OK else TokenValidationResult.FORBIDDEN
		if (authentication is TokenAuthentication) {
			// Texas har verifisert tokenet, så klient-appen kan brukes. TokenX først, som i SpringSecuritySubjectHandler
			val issuer = TokenIssuer.entries.firstOrNull { authentication.token(it) != null }
			val token = issuer?.let(authentication::token)
			return TokenValidationOutcome(
				result = grantedOrForbidden,
				issuer = TokenValidationOutcome.issuerLabel(issuer),
				client = if (issuer != null && token != null) clientLabels.of(token, issuer) else TokenValidationOutcome.UNKNOWN,
			)
		}
		if (authentication != null && authentication !is AnonymousAuthenticationToken && authentication.isAuthenticated) {
			// Autentisert på annen måte enn med token fra Texas. Ingen claims brukes
			return TokenValidationOutcome(grantedOrForbidden, TokenValidationOutcome.UNKNOWN, TokenValidationOutcome.UNKNOWN)
		}

		// Ikke verifisert, så ingen claims fra tokenet brukes
		val check = request.getAttribute(TokenCheck.ATTRIBUTE) as? TokenCheck
		val result = when {
			granted -> TokenValidationResult.OK
			check == null || check.result == TokenValidationResult.OK -> TokenValidationResult.MISSING
			else -> check.result
		}
		return TokenValidationOutcome(result, TokenValidationOutcome.issuerLabel(check?.issuer), TokenValidationOutcome.UNKNOWN)
	}

	// AnonymousAuthenticationFilter setter alltid en autentisering i HTTP-forespørsler
	private fun authentication(): Authentication =
		SecurityContextHolder.getContext().authentication
			?: throw AuthenticationCredentialsNotFoundException("Ingen autentisering i forespørselen")
}
