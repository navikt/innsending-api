package no.nav.soknad.innsending.db

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import javax.annotation.PostConstruct
import javax.sql.DataSource

@Profile("test | default | spring")
@Configuration
class StartDb {

	private lateinit var embeddedPostgres: EmbeddedPostgres

	init {
		embeddedPostgres = EmbeddedPostgres.builder().start()
	}

	@Bean
	fun embeddedPostgres(): DataSource {
		return embeddedPostgres.getPostgresDatabase()
	}

}
