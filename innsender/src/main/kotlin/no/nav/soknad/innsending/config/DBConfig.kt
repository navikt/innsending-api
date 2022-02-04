package no.nav.soknad.innsending.config

import no.nav.soknad.innsending.ProfileConfig
import org.springframework.boot.context.properties.ConfigurationProperties
import kotlin.properties.Delegates

@ConfigurationProperties(prefix = "dbconfig")
class DBConfig(private val profileConfig: ProfileConfig) {
	lateinit var profiles: String
	lateinit var  databaseName: String
	lateinit var  mountPathVault: String
	lateinit var  databaseUrl: String
	var embedded by Delegates.notNull<Boolean>()
	var useVault by Delegates.notNull<Boolean>()
}

