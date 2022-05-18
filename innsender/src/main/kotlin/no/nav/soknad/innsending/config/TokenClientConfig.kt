package no.nav.soknad.innsending.config

import no.nav.security.token.support.client.spring.oauth2.EnableOAuth2Client
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@EnableOAuth2Client(cacheEnabled = true)
@Configuration
@Profile("dev | prod")
class TokenClientConfig

