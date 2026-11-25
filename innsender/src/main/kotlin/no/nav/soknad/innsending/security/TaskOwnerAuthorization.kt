package no.nav.soknad.innsending.security

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class TaskOwnerAuthorization(
	objectMapper: ObjectMapper,
	@Value("\${task-authorization.pre-authorized-apps:[]}") preAuthorizedApps: String,
	@Value("\${task-authorization.allowed-applications:}") allowedApplications: String,
) {
	private val logger = LoggerFactory.getLogger(javaClass)
	private val authorizedClientIds: Set<String> = try {
		val allowedNames = if (allowedApplications.isBlank()) emptySet() else {
			allowedApplications.split(',').map(String::trim).onEach {
				require(APPLICATION_NAME.matches(it))
			}.toSet()
		}
		val apps = objectMapper.reader()
			.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
			.with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
			.readTree(preAuthorizedApps)
		require(apps != null && apps.isArray)
		val clients = apps.map { app ->
			require(app.isObject)
			val clientId = app.get("clientId")
			val name = app.get("name")
			require(clientId != null && clientId.isTextual && clientId.asText().isNotBlank())
			require(name != null && name.isTextual && APPLICATION_NAME.matches(name.asText()))
			clientId.asText() to name.asText()
		}
		require(clients.map { it.first }.distinct().size == clients.size)
		clients.filter { it.second in allowedNames }.map { it.first }.toSet()
	} catch (_: Exception) {
		logger.error("Invalid task authorization configuration; all task API callers are denied")
		emptySet()
	}

	// Called only after the controller's Azure issuer, signature and audience validation.
	fun isAuthorizedApplication(authentication: Authentication?): Boolean =
		authorizedClientId(authentication) != null

	fun requireAuthorizedApplication(): String =
		authorizedClientId(SecurityContextHolder.getContext().authentication) ?: deny()

	private fun authorizedClientId(authentication: Authentication?): String? {
		val tokenAuth = authentication as? TokenAuthentication ?: return null
		val azure = tokenAuth.token(TokenIssuer.AZURE) ?: return null
		val subject = azure.stringClaim("sub")
		val objectId = azure.stringClaim("oid")
		val clientId = azure.stringClaim("azp")
		if (subject.isNullOrBlank() || subject != objectId || clientId !in authorizedClientIds) return null
		return clientId
	}

	private fun deny(): Nothing = throw AccessDeniedException("Caller is not authorized for the task API")

	companion object {
		private val APPLICATION_NAME = Regex("[a-z0-9][a-z0-9-]*:[a-z0-9][a-z0-9-]*:[a-z0-9][a-z0-9-]*")
	}
}
