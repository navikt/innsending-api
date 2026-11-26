package no.nav.soknad.innsending.utils

import org.flywaydb.core.Flyway
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestContext
import org.springframework.test.context.support.AbstractTestExecutionListener
import javax.sql.DataSource

/**
 * Tømmer databasen før hver testklasse. Spring gjenbruker testkontekster, og dermed databasen, på tvers av klasser.
 * Uten tømming finner for eksempel en ny ettersending søknader som en tidligere klasse lagret for samme bruker.
 *
 * Historikken til Flyway og tabellene med startdata fra Flyway blir stående. Testene som trenger en bestemt verdi
 * i config, setter den selv.
 */
class EmptyDatabaseBeforeTestClass : AbstractTestExecutionListener() {

	override fun beforeTestClass(testContext: TestContext) {
		val applicationContext = testContext.applicationContext
		val jdbcTemplate = JdbcTemplate(applicationContext.getBean(DataSource::class.java))
		val flywayHistoryTable = applicationContext.getBean(Flyway::class.java).configuration.table
		val tables = jdbcTemplate.queryForList(
			"SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename NOT IN (?, 'config', 'alive')",
			String::class.java,
			flywayHistoryTable,
		)
		if (tables.isNotEmpty()) {
			jdbcTemplate.execute("TRUNCATE TABLE ${tables.joinToString { "\"$it\"" }} RESTART IDENTITY CASCADE")
		}
	}
}
