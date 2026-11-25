package no.nav.soknad.innsending.security

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TaskOwnerAuthorizationTest {
	private val preAuthorizedApps = """[{"clientId":"task-client-id","name":"dev-gcp:team-soknad:task-client"},{"clientId":"other-client-id","name":"dev-gcp:team-soknad:other-client"}]"""
	private val allowedApplications = "dev-gcp:team-soknad:task-client"

	@AfterEach
	fun cleanUp() = SecurityContextHolder.clearContext()

	@Test
	fun `should return azp for an authorized application token`() {
		withClaims()
		assertEquals("task-client-id", authorization().requireAuthorizedApplication())
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = ["unknown-client-id", "other-client-id", " "])
	fun `should deny missing unknown or non-task azp`(azp: String?) {
		withClaims(azp = azp)
		assertFailsWith<AccessDeniedException> { authorization().requireAuthorizedApplication() }
	}

	@Test
	fun `should not use client_id as a fallback for azp`() {
		withClaims(azp = null, additionalClaims = mapOf("client_id" to "task-client-id"))
		assertFailsWith<AccessDeniedException> { authorization().requireAuthorizedApplication() }
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = ["user-object-id", " "])
	fun `should deny tokens without matching nonblank sub and oid`(objectId: String?) {
		withClaims(objectId = objectId)
		assertFailsWith<AccessDeniedException> { authorization().requireAuthorizedApplication() }
	}

	@Test
	fun `should deny matching blank sub and oid`() {
		withClaims(subject = "", objectId = "")
		assertFailsWith<AccessDeniedException> { authorization().requireAuthorizedApplication() }
	}

	@Test
	fun `should deny non-string azp and oid claims`() {
		withClaims(azp = listOf("task-client-id"))
		assertFailsWith<AccessDeniedException> { authorization().requireAuthorizedApplication() }
		withClaims(objectId = listOf("service-principal"))
		assertFailsWith<AccessDeniedException> { authorization().requireAuthorizedApplication() }
	}

	@Test
	fun `should not use TokenX when no Azure token is available`() {
		SecurityContextHolder.getContext().authentication = TokenAuthentication(
			TokenIssuer.TOKENX,
			mapOf("sub" to "service-principal", "oid" to "service-principal", "azp" to "task-client-id"),
			"token",
		)
		assertFailsWith<AccessDeniedException> { authorization().requireAuthorizedApplication() }
	}

	@Test
	fun `should deny missing or non-token authentication`() {
		assertFailsWith<AccessDeniedException> { authorization().requireAuthorizedApplication() }
		SecurityContextHolder.getContext().authentication = TestingAuthenticationToken("principal", null)
		assertFailsWith<AccessDeniedException> { authorization().requireAuthorizedApplication() }
	}

	@ParameterizedTest
	@ValueSource(strings = [
		"", "[]", "not-json", "null", "{}", "[null]",
		"[{\"clientId\":\"task-client-id\"}]",
		"[{\"clientId\":123,\"name\":\"dev-gcp:team-soknad:task-client\"}]",
		"[{\"clientId\":\"\",\"name\":\"dev-gcp:team-soknad:task-client\"}]",
		"[{\"clientId\":\"task-client-id\",\"name\":\"task-client\"}]",
		"[{\"clientId\":\"task-client-id\",\"name\":[\"dev-gcp:team-soknad:task-client\"]}]",
		"[{\"clientId\":\"task-client-id\",\"name\":\"dev-gcp:team-soknad:other-client\",\"name\":\"dev-gcp:team-soknad:task-client\"}]",
	])
	fun `should deny all callers for empty or malformed preauthorization configuration`(configuration: String) {
		withClaims()
		assertFailsWith<AccessDeniedException> {
			authorization(preAuthorizedApps = configuration).requireAuthorizedApplication()
		}
	}

	@Test
	fun `should deny all callers for ambiguous client identities`() {
		withClaims()
		val duplicate = """[{"clientId":"task-client-id","name":"dev-gcp:team-soknad:task-client"},{"clientId":"task-client-id","name":"dev-gcp:team-soknad:other-client"}]"""
		assertFailsWith<AccessDeniedException> {
			authorization(preAuthorizedApps = duplicate).requireAuthorizedApplication()
		}
	}

	@Test
	fun `should deny malformed entries even alongside a valid authorized application`() {
		withClaims()
		val malformed = preAuthorizedApps.dropLast(1) + ",{}]"
		assertFailsWith<AccessDeniedException> {
			authorization(preAuthorizedApps = malformed).requireAuthorizedApplication()
		}
	}

	@Test
	fun `should deny trailing JSON after valid preauthorization configuration`() {
		withClaims()
		assertFailsWith<AccessDeniedException> {
			authorization(preAuthorizedApps = "$preAuthorizedApps []").requireAuthorizedApplication()
		}
	}

	@ParameterizedTest
	@ValueSource(strings = ["", " ", "task-client", "*", "dev-gcp:team-soknad:task-client,", "dev-gcp:team-soknad:task-client,*", "dev-gcp.team-soknad.task-client"])
	fun `should deny all callers for empty or malformed task allowlist`(configuration: String) {
		withClaims()
		assertFailsWith<AccessDeniedException> {
			authorization(allowedApplications = configuration).requireAuthorizedApplication()
		}
	}

	@Test
	fun `should match the exact configured application including cluster and namespace`() {
		withClaims()
		assertFailsWith<AccessDeniedException> {
			authorization(allowedApplications = "prod-gcp:team-soknad:task-client").requireAuthorizedApplication()
		}
		assertFailsWith<AccessDeniedException> {
			authorization(allowedApplications = "dev-gcp:other-team:task-client").requireAuthorizedApplication()
		}
	}

	private fun authorization(
		preAuthorizedApps: String = this.preAuthorizedApps,
		allowedApplications: String = this.allowedApplications,
	) = TaskOwnerAuthorization(jacksonObjectMapper(), preAuthorizedApps, allowedApplications)

	private fun withClaims(
		azp: Any? = "task-client-id",
		subject: String = "service-principal",
		objectId: Any? = subject,
		additionalClaims: Map<String, Any> = emptyMap(),
	) {
		val claims = buildMap<String, Any?> {
			put("sub", subject)
			if (azp != null) put("azp", azp)
			if (objectId != null) put("oid", objectId)
			putAll(additionalClaims)
		}
		SecurityContextHolder.getContext().authentication = TokenAuthentication(TokenIssuer.AZURE, claims, "token")
	}
}
