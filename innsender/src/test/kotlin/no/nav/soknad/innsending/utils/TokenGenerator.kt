package no.nav.soknad.innsending.utils

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import com.nimbusds.jwt.SignedJWT
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.security.mock.oauth2.token.KeyProvider
import no.nav.security.mock.oauth2.token.OAuth2TokenProvider
import no.nav.soknad.innsending.util.Constants.AZURE
import no.nav.soknad.innsending.util.Constants.TOKENX
import java.util.Date
import java.util.UUID

class TokenGenerator(
	private val mockOAuth2Server: MockOAuth2Server,
) {
	companion object {
		val subject = "12345678901"
		const val ACCEPTED_AUDIENCE = "aud-localhost"
		const val TOKENX_CLIENT_ID = "application"
		const val AZURE_CLIENT_ID = "consumer-app-id"
		const val ACR_LEVEL_4 = "Level4"
		const val ACR_IDPORTEN_LOA_HIGH = "idporten-loa-high"
		const val ACR_IDPORTEN_LOA_SUBSTANTIAL = "idporten-loa-substantial"
	}

	private val defaultExpiry = 2 * 3600L

	fun lagTokenXToken(
		fnr: String? = null,
		acr: String? = ACR_IDPORTEN_LOA_HIGH,
		audience: String = ACCEPTED_AUDIENCE,
		expiry: Long = defaultExpiry,
		issuerId: String = TOKENX,
	): String {
		val pid = fnr ?: subject
		return mockOAuth2Server.issueToken(
			issuerId = issuerId,
			clientId = TOKENX_CLIENT_ID,
			tokenCallback = DefaultOAuth2TokenCallback(
				issuerId = issuerId,
				subject = pid,
				typeHeader = JOSEObjectType.JWT.type,
				audience = listOf(audience),
				claims = buildMap {
					put("pid", pid)
					put("client_id", TOKENX_CLIENT_ID)
					put("idp", "https://test.idporten.no")
					if (acr != null) put("acr", acr)
				},
				expiry = expiry
			)
		).serialize()
	}

	fun lagAzureM2MToken(
		roles: List<String> = emptyList(),
		audience: String = ACCEPTED_AUDIENCE,
		expiry: Long = defaultExpiry,
	): String {
		val objectId = UUID.randomUUID().toString()
		val oAuth2TokenCallback = DefaultOAuth2TokenCallback(
			issuerId = AZURE,
			subject = objectId,
			typeHeader = JOSEObjectType.JWT.type,
			audience = listOf(audience),
			claims = buildMap {
				put("azp", AZURE_CLIENT_ID)
				put("azp_name", "dev-gcp:namespace:consumer-app-name")
				put("oid", objectId)
				put("idtyp", "app")
				put("roles", listOf("access_as_application").plus(roles))
			},
			expiry = expiry
		)
		return mockOAuth2Server.issueToken(
			issuerId = AZURE,
			clientId = AZURE_CLIENT_ID,
			tokenCallback = oAuth2TokenCallback
		).serialize()
	}

	// Gir scp="<scopes> defaultaccess", samme rekkefølge som innsending-admin sender i dag
	fun lagAzureOBOToken(scopes: String? = null, navIdent: String? = null): String =
		lagAzureOBOTokenWithScp(scp = "${scopes?.let { "$it " } ?: ""}defaultaccess", navIdent = navIdent)

	fun lagAzureOBOTokenWithScp(
		scp: String,
		navIdent: String? = null,
		audience: String = ACCEPTED_AUDIENCE,
		expiry: Long = defaultExpiry,
	): String {
		val oAuth2TokenCallback = DefaultOAuth2TokenCallback(
			issuerId = AZURE,
			subject = UUID.randomUUID().toString(),
			typeHeader = JOSEObjectType.JWT.type,
			audience = listOf(audience),
			claims = buildMap {
				put("name", "Ola Nordmann")
				put("preferred_username", "Ola.Nordmann@test.no")
				put("azp", AZURE_CLIENT_ID)
				put("azp_name", "dev-gcp:namespace:consumer-app-name")
				put("oid", UUID.randomUUID().toString())
				put("scp", scp)
				if (navIdent != null) {
					put("NAVident", navIdent)
				}
			},
			expiry = expiry
		)
		return mockOAuth2Server.issueToken(
			issuerId = AZURE,
			clientId = AZURE_CLIENT_ID,
			tokenCallback = oAuth2TokenCallback
		).serialize()
	}

	// Gyldige TokenX-claims, men signert med en nøkkel som ikke finnes i JWKS-en til issueren
	fun lagTokenXTokenSignedWithUnknownKey(): String {
		val unknownKey = RSAKeyGenerator(2048).keyID("ukjent-${UUID.randomUUID()}").generate()
		val jwt = SignedJWT(
			JWSHeader.Builder(JWSAlgorithm.RS256).keyID(unknownKey.keyID).type(JOSEObjectType.JWT).build(),
			tokenXClaims()
		)
		jwt.sign(RSASSASigner(unknownKey))
		return jwt.serialize()
	}

	// Gyldige TokenX-claims uten signatur (alg=none)
	fun lagUnsignedTokenXToken(): String = PlainJWT(tokenXClaims()).serialize()

	/**
	 * Gyldige TokenX-claims uten de oppgitte claimene, signert med issuerens ekte nøkkel.
	 * mock-oauth2-server setter alltid exp, iat og sub, så tokenet må signeres utenom serveren.
	 */
	fun lagTokenXTokenWithoutClaims(vararg omitted: String): String {
		val claims = JWTClaimsSet.Builder(tokenXClaims()).apply { omitted.forEach { claim(it, null) } }.build()
		val signingKey = issuerSigningKey(TOKENX)
		val jwt = SignedJWT(
			JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.keyID).type(JOSEObjectType.JWT).build(),
			claims
		)
		jwt.sign(RSASSASigner(signingKey))
		return jwt.serialize()
	}

	// mock-oauth2-server eksponerer ikke signeringsnøkkelen, så vi henter den med refleksjon. Bare for test.
	private fun issuerSigningKey(issuerId: String): RSAKey {
		val tokenProvider = mockOAuth2Server.config.tokenProvider
		val keyProviderField = OAuth2TokenProvider::class.java.getDeclaredField("keyProvider").apply { isAccessible = true }
		val keyProvider = keyProviderField.get(tokenProvider) as KeyProvider
		return keyProvider.signingKey(issuerId).toRSAKey()
	}

	private fun tokenXClaims(): JWTClaimsSet {
		val now = Date()
		return JWTClaimsSet.Builder()
			.issuer(mockOAuth2Server.issuerUrl(TOKENX).toString())
			.subject(subject)
			.audience(ACCEPTED_AUDIENCE)
			.issueTime(now)
			.notBeforeTime(now)
			.expirationTime(Date(now.time + defaultExpiry * 1000))
			.jwtID(UUID.randomUUID().toString())
			.claim("pid", subject)
			.claim("client_id", TOKENX_CLIENT_ID)
			.claim("acr", ACR_IDPORTEN_LOA_HIGH)
			.build()
	}

}
