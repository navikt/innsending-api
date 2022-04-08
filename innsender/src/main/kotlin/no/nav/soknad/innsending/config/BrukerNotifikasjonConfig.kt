package no.nav.soknad.innsending.config

import no.nav.soknad.innsending.ProfileConfig
import org.springframework.boot.context.properties.ConfigurationProperties
import kotlin.properties.Delegates

@ConfigurationProperties(prefix = "brukernotifikasjonconfig")
class BrukerNotifikasjonConfig(private val profileConfig: ProfileConfig) {
	lateinit var profiles: String
	lateinit var tjenesteUrl: String
	lateinit var gjenopptaSoknadsArbeid: String
	lateinit var ettersendePaSoknad: String
	var publisereEndringer by Delegates.notNull<Boolean>()

	fun setProfiles() {profiles = profileConfig.profil}

}
