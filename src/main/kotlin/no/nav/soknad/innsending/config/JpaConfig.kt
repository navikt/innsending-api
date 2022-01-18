package no.nav.soknad.innsending.config

import com.zaxxer.hikari.HikariDataSource
import no.nav.soknad.innsending.ApplicationState
import no.nav.soknad.innsending.db.*
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JpaConfig(
	private val dbConfig: DBConfig,
	private val appState: ApplicationState) {

	private val logger = LoggerFactory.getLogger(javaClass)

	@Bean
	fun getDataSource() = initDatasource()

	private fun initDatasource(): HikariDataSource {
		val credentialService = if (dbConfig.embedded) EmbeddedCredentialService() else VaultCredentialService()
		val renewService = if (dbConfig.embedded) EmbeddedRenewService(credentialService) else RenewVaultService(credentialService)
		val database = if (dbConfig.embedded) EmbeddedDatabase(dbConfig, credentialService) else Database(dbConfig, credentialService)

		appState.ready = true
		renewService.startRenewTasks(appState)
		logger.info("Datasource is initialised")
		return database.dataSource
	}
}
