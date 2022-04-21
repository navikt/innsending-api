package no.nav.soknad.innsending.config

import com.zaxxer.hikari.HikariDataSource
import no.nav.soknad.innsending.ApplicationState
import no.nav.soknad.innsending.ProfileConfig
import no.nav.soknad.innsending.db.*
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/*
@Configuration
@EnableConfigurationProperties(DBConfig::class)
class JpaConfig(
	private val profileConfig: ProfileConfig,
	private val dbConfig: DBConfig,
	private val appState: ApplicationState) {

	private val logger = LoggerFactory.getLogger(javaClass)

	@Bean
	fun getDataSource() = initDatasource()

	private fun initDatasource(): HikariDataSource {
		val profil = profileConfig.profil
		val env = System.getenv("SPRING_PROFILES_ACTIVE")
		logger.info("profil=$profil, env=$env")
		logger.info("Profile=${dbConfig.profiles}. Embedded=${dbConfig.embedded}. Klar for å initialisere database, ${dbConfig.databaseName} på ${dbConfig.databaseUrl}")
		val credentialService = if (dbConfig.embedded) EmbeddedCredentialService() else VaultCredentialService()
		val renewService = if (dbConfig.embedded) EmbeddedRenewService(credentialService) else RenewVaultService(credentialService)
		val database = if (dbConfig.embedded) EmbeddedDatabase(dbConfig, credentialService) else Database(dbConfig, credentialService)

		appState.ready = true
		renewService.startRenewTasks(appState)
		logger.info("Datasource is initialised")
		return database.dataSource
	}
}
*/
