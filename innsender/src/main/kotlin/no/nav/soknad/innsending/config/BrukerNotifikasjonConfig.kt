package no.nav.soknad.innsending.config

import org.springframework.boot.context.properties.ConfigurationProperties
import kotlin.properties.Delegates

@ConfigurationProperties(prefix = "brukernotifikasjonconfig")
class BrukerNotifikasjonConfig {
	lateinit var fyllutUrl: String
	lateinit var sendinnUrl: String
	var publisereEndringer by Delegates.notNull<Boolean>()

}
