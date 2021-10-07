package no.nav.soknad.innsending.db

import com.zaxxer.hikari.HikariDataSource

data class RenewCredentialsTaskData(
	val dataSource: HikariDataSource,
	val mountPath: String,
	val databaseName: String,
	val role: Role
)

data class Credentials(
	val leaseId: String,
	val username: String,
	val password: String
)
