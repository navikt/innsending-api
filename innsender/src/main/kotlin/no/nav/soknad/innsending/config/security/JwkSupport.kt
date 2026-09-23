package no.nav.soknad.innsending.config.security

import com.nimbusds.jose.jwk.JWK
import java.nio.file.Files
import java.nio.file.Path

/**
 * I prod/dev er NAIS-injiserte nøkler (TOKEN_X_PRIVATE_JWK, AZURE_APP_JWK) selve JWK-en som
 * JSON. I tester brukes gjerne en filsti til en JWK-fil i stedet. Denne funksjonen støtter
 * begge deler, slik at samme konfigurasjon kan brukes i application.yml og application-test.yml.
 */
internal fun resolvePrivateJwk(jwkValue: String): JWK {
	val jwkJson = if (jwkValue.trim().startsWith("{")) jwkValue else Files.readString(Path.of(jwkValue))
	return JWK.parse(jwkJson)
}
