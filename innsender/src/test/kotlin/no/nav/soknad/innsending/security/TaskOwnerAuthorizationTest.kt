package no.nav.soknad.innsending.security

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nimbusds.jwt.JWTClaimsSet
import io.mockk.every
import io.mockk.mockk
import no.nav.security.token.support.core.context.TokenValidationContext
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.security.token.support.core.jwt.JwtTokenClaims
import no.nav.security.token.support.spring.validation.interceptor.JwtTokenUnauthorizedException
import no.nav.soknad.innsending.util.Constants.AZURE
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TaskOwnerAuthorizationTest {
	private val contextHolder = mockk<TokenValidationContextHolder>()
	private val context = mockk<TokenValidationContext>()
	private val preAuthorizedApps = """[{"clientId":"task-client-id","name":"dev-gcp:team-soknad:task-client"},{"clientId":"other-client-id","name":"dev-gcp:team-soknad:other-client"}]"""
	private val allowedApplications = "dev-gcp:team-soknad:task-client"

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
		assertFailsWith<JwtTokenUnauthorizedException> { authorization().requireAuthorizedApplication() }
	}

	@Test
	fun `should not use client_id as a fallback for azp`() {
		withClaims(azp = null, additionalClaims = mapOf("client_id" to "task-client-id"))
		assertFailsWith<JwtTokenUnauthorizedException> { authorization().requireAuthorizedApplication() }
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = ["user-object-id", " "])
	fun `should deny tokens without matching nonblank sub and oid`(objectId: String?) {
		withClaims(objectId = objectId)
		assertFailsWith<JwtTokenUnauthorizedException> { authorization().requireAuthorizedApplication() }
	}

	@Test
	fun `should deny matching blank sub and oid`() {
		withClaims(subject = "", objectId = "")
		assertFailsWith<JwtTokenUnauthorizedException> { authorization().requireAuthorizedApplication() }
	}

	@Test
	fun `should deny non-string azp and oid claims`() {
		withClaims(azp = listOf("task-client-id"))
		assertFailsWith<JwtTokenUnauthorizedException> { authorization().requireAuthorizedApplication() }
		withClaims(objectId = listOf("service-principal"))
		assertFailsWith<JwtTokenUnauthorizedException> { authorization().requireAuthorizedApplication() }
	}

	@Test
	fun `should not use TokenX when no Azure token is available`() {
		every { contextHolder.getTokenValidationContext() } returns context
		every { context.hasTokenFor(AZURE) } returns false
		assertFailsWith<JwtTokenUnauthorizedException> { authorization().requireAuthorizedApplication() }
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
		assertFailsWith<JwtTokenUnauthorizedException> {
			authorization(preAuthorizedApps = configuration).requireAuthorizedApplication()
		}
	}

	@Test
	fun `should deny all callers for ambiguous client identities`() {
		withClaims()
		val duplicate = """[{"clientId":"task-client-id","name":"dev-gcp:team-soknad:task-client"},{"clientId":"task-client-id","name":"dev-gcp:team-soknad:other-client"}]"""
		assertFailsWith<JwtTokenUnauthorizedException> {
			authorization(preAuthorizedApps = duplicate).requireAuthorizedApplication()
		}
	}

	@Test
	fun `should deny malformed entries even alongside a valid authorized application`() {
		withClaims()
		val malformed = preAuthorizedApps.dropLast(1) + ",{}]"
		assertFailsWith<JwtTokenUnauthorizedException> {
			authorization(preAuthorizedApps = malformed).requireAuthorizedApplication()
		}
	}

	@Test
	fun `should deny trailing JSON after valid preauthorization configuration`() {
		withClaims()
		assertFailsWith<JwtTokenUnauthorizedException> {
			authorization(preAuthorizedApps = "$preAuthorizedApps []").requireAuthorizedApplication()
		}
	}

	@ParameterizedTest
	@ValueSource(strings = ["", " ", "task-client", "*", "dev-gcp:team-soknad:task-client,", "dev-gcp:team-soknad:task-client,*", "dev-gcp.team-soknad.task-client"])
	fun `should deny all callers for empty or malformed task allowlist`(configuration: String) {
		withClaims()
		assertFailsWith<JwtTokenUnauthorizedException> {
			authorization(allowedApplications = configuration).requireAuthorizedApplication()
		}
	}

	@Test
	fun `should match the exact configured application including cluster and namespace`() {
		withClaims()
		assertFailsWith<JwtTokenUnauthorizedException> {
			authorization(allowedApplications = "prod-gcp:team-soknad:task-client").requireAuthorizedApplication()
		}
		assertFailsWith<JwtTokenUnauthorizedException> {
			authorization(allowedApplications = "dev-gcp:other-team:task-client").requireAuthorizedApplication()
		}
	}

	private fun authorization(
		preAuthorizedApps: String = this.preAuthorizedApps,
		allowedApplications: String = this.allowedApplications,
	) = TaskOwnerAuthorization(contextHolder, jacksonObjectMapper(), preAuthorizedApps, allowedApplications)

	private fun withClaims(
		azp: Any? = "task-client-id",
		subject: String = "service-principal",
		objectId: Any? = subject,
		additionalClaims: Map<String, Any> = emptyMap(),
	) {
		val claims = JWTClaimsSet.Builder().subject(subject).apply {
			if (azp != null) claim("azp", azp)
			if (objectId != null) claim("oid", objectId)
			additionalClaims.forEach { (key, value) -> claim(key, value) }
		}.build()
		every { contextHolder.getTokenValidationContext() } returns context
		every { context.hasTokenFor(AZURE) } returns true
		every { context.getClaims(AZURE) } returns JwtTokenClaims(claims)
	}
}
