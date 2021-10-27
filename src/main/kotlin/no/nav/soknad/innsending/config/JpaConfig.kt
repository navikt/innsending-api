package no.nav.soknad.innsending.config

import com.zaxxer.hikari.HikariDataSource
import no.nav.soknad.innsending.db.Database
import no.nav.soknad.innsending.db.EmbeddedDatabase
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JpaConfig(private val appConfig: AppConfiguration) {

	private val logger = LoggerFactory.getLogger(javaClass)

	@Bean
	fun getDataSource() = initDatasource()

	private fun initDatasource(): HikariDataSource {
		val database = if (appConfig.dbConfig.embedded) {
			EmbeddedDatabase(appConfig.dbConfig, appConfig.dbConfig.credentialService)
		} else {
			Database(appConfig.dbConfig, appConfig.dbConfig.credentialService)
		}
		appConfig.applicationState.ready = true
		appConfig.dbConfig.renewService.startRenewTasks(appConfig.applicationState)
		logger.info("Datasource is initialised")
		return database.dataSource
	}
}
