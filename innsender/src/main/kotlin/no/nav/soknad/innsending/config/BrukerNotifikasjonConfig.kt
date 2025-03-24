package no.nav.soknad.innsending.config

import org.springframework.boot.context.properties.ConfigurationProperties
import kotlin.properties.Delegates

@ConfigurationProperties(prefix = "brukernotifikasjonconfig")
class BrukerNotifikasjonConfig {
	var publisereEndringer by Delegates.notNull<Boolean>()
}
