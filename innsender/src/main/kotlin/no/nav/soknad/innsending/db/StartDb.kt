package no.nav.soknad.innsending.db

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.util.UUID
import javax.sql.DataSource

@Profile("test", "local")
@Configuration
class StartDb {

	// Every Spring context gets its own database in the shared Postgres container, so contexts do not see each other's data.
	@Bean
	fun embeddedPostgres(): DataSource {
		val databaseName = "innsending_" + UUID.randomUUID().toString().replace("-", "")
		postgres.postgresDatabase.connection.use { it.createStatement().use { statement -> statement.execute("CREATE DATABASE $databaseName") } }
		return postgres.getDatabase("postgres", databaseName)
	}

	companion object {
		// One Postgres container per JVM. Starting it for each Spring context took about a second per test class.
		private val postgres: EmbeddedPostgres by lazy {
			EmbeddedPostgres.builder().setTag("18").start().also { started ->
				Runtime.getRuntime().addShutdownHook(Thread { started.close() })
			}
		}
	}

}
