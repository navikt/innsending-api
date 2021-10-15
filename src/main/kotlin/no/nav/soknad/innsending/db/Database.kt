package no.nav.soknad.innsending.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.soknad.innsending.config.AppConfiguration
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.sql.Connection

class Database(
	private val env: AppConfiguration.DBConfig,
	private val vaultCredentialService: CredentialService
) : DatabaseInterface {
	private val logger = LoggerFactory.getLogger(javaClass)

	override val dataSource: HikariDataSource

	override val connection: Connection
		get() = dataSource.connection

	init {
		logger.info("Database init")
		runFlywayMigrations()

		val initialCredentials = vaultCredentialService.getNewCredentials(
			mountPath = env.mountPathVault,
			databaseName = env.databaseName,
			role = Role.USER
		)

		logger.info("Database init. Set datasource")
		dataSource = HikariDataSource(HikariConfig().apply {
			jdbcUrl = env.url
			username = initialCredentials.username
			password = initialCredentials.password
			maximumPoolSize = 3
			minimumIdle = 1
			idleTimeout = 10001
			maxLifetime = 300000
			isAutoCommit = false
			transactionIsolation = "TRANSACTION_REPEATABLE_READ"
			validate()
		})

		logger.info("Database init. Start RenewCredentialsTaskData")
		vaultCredentialService.setRenewCredentialsTaskData(
			dataSource = dataSource,
			mountPath = env.mountPathVault,
			databaseName = env.databaseName,
			role = Role.USER
		)
	}

	private fun runFlywayMigrations() = Flyway.configure().run {
		val credentials = vaultCredentialService.getNewCredentials(
			mountPath = env.mountPathVault,
			databaseName = env.databaseName,
			role = Role.ADMIN
		)
		dataSource(env.url, credentials.username, credentials.password)
		if (!"docker".equals(env.profiles, true)) {
			val sql = "SET ROLE \"${env.databaseName}-${Role.ADMIN}\""
			initSql(sql) // required for assigning proper owners for the tables
			logger.info("Database, runFlywayMigrations: $sql")
		} else {
			logger.info("Database, runFlywayMigrations without setting role")
		}
		load().migrate()
	}
}
