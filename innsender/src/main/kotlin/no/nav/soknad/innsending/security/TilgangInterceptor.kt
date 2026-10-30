package no.nav.soknad.innsending.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.util.SimpleMethodInvocation
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * Sjekker `@PreAuthorize` før Spring leser og validerer request-bodyen (#461).
 *
 * Metodesikkerheten i Spring Security slår først til når controller-metoden kalles, etter at argumentene er bundet.
 * Uten denne sjekken ville et kall uten gyldig token mot et endepunkt med body fått 400 eller 500 fra valideringen
 * i stedet for 403, og lært noe om endepunktet. Token-support sjekker på samme sted, i en interceptor.
 * Avvisning gir [org.springframework.security.authorization.AuthorizationDeniedException], som RestExceptionHandler
 * gjør om til 403.
 */
class TilgangInterceptor : HandlerInterceptor {

	private val preAuthorize = PreAuthorizeAuthorizationManager()

	override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
		if (handler is HandlerMethod) {
			preAuthorize.verify(::autentisering, SimpleMethodInvocation(handler.bean, handler.method))
		}
		return true
	}

	// AnonymousAuthenticationFilter setter alltid en autentisering i HTTP-forespørsler
	private fun autentisering(): Authentication =
		SecurityContextHolder.getContext().authentication
			?: throw AuthenticationCredentialsNotFoundException("Ingen autentisering i forespørselen")
}
