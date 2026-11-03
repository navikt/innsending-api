package no.nav.soknad.innsending.security

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.security.token.support.spring.validation.interceptor.JwtTokenUnauthorizedException
import no.nav.soknad.innsending.util.Constants.AZURE
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class TaskOwnerAuthorization(
	private val contextHolder: TokenValidationContextHolder,
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
	fun requireAuthorizedApplication(): String {
		val context = contextHolder.getTokenValidationContext()
		if (!context.hasTokenFor(AZURE)) deny()
		val claims = context.getClaims(AZURE)
		val subject = claims.get("sub") as? String
		val objectId = claims.get("oid") as? String
		val clientId = claims.get("azp") as? String
		if (subject.isNullOrBlank() || subject != objectId || clientId !in authorizedClientIds) deny()
		return clientId ?: deny()
	}

	private fun deny(): Nothing = throw JwtTokenUnauthorizedException("Caller is not authorized for the task API")

	companion object {
		private val APPLICATION_NAME = Regex("[a-z0-9][a-z0-9-]*:[a-z0-9][a-z0-9-]*:[a-z0-9][a-z0-9-]*")
	}
}
