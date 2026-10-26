package no.nav.soknad.innsending.repository

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.opentable.db.postgres.embedded.EmbeddedPostgres
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.utils.ApplicationOwnerConverter
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.AbstractDataSource
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OwnerMigrationTest {

	private lateinit var postgres: EmbeddedPostgres
	private lateinit var schemaName: String
	private lateinit var dataSource: DataSource
	private lateinit var jdbc: JdbcTemplate

	@BeforeAll
	fun startDatabase() {
		postgres = EmbeddedPostgres.builder().setTag("18").start()
	}

	@AfterAll
	fun stopDatabase() {
		postgres.close()
	}

	@BeforeEach
	fun migrateIsolatedSchemaToPreviousVersion() {
		schemaName = "owner_migration_" + UUID.randomUUID().toString().replace("-", "")
		// The identifier is generated here, never supplied by a caller.
		JdbcTemplate(postgres.postgresDatabase).execute("CREATE SCHEMA $schemaName")
		dataSource = object : AbstractDataSource() {
			override fun getConnection(): Connection = postgres.postgresDatabase.connection.apply { schema = schemaName }
			override fun getConnection(username: String, password: String): Connection = connection
		}
		jdbc = JdbcTemplate(dataSource)
		migrate("1.43")
	}

	@AfterEach
	fun removeIsolatedSchema() {
		JdbcTemplate(postgres.postgresDatabase).execute("DROP SCHEMA $schemaName CASCADE")
	}

	@Test
	fun `display type persisted ordinals remain stable for legacy writers`() {
		assertEquals(PERSISTED_DISPLAY_TYPES, VisningsType.values().associate { it.name to it.ordinal.toString() })
		PERSISTED_DISPLAY_TYPES.forEach { (name, ordinal) -> insertLegacy(name, ordinal) }
		assertEquals(PERSISTED_DISPLAY_TYPES, jdbc.query("SELECT innsendingsid, visningstype FROM soknad") { row, _ ->
			row.getString("innsendingsid") to row.getString("visningstype")
		}.toMap())
	}

	@Test
	fun `migration adds a nullable JSONB owner column without a default`() {
		migrate("1.44")
		val column = jdbc.queryForMap(
			"""
			SELECT data_type, is_nullable, column_default
			FROM information_schema.columns
			WHERE table_schema = ? AND table_name = 'soknad' AND column_name = 'owner'
			""".trimIndent(),
			schemaName,
		)
		assertEquals("jsonb", column["data_type"])
		assertEquals("YES", column["is_nullable"])
		assertNull(column["column_default"])
	}

	@Test
	fun `migration preserves existing rows across all display types and statuses without filling owners`() {
		PERSISTED_DISPLAY_TYPES.values.forEach { displayType ->
			SoknadsStatus.values().forEach { status ->
				val submissionId = "existing-$displayType-${status.name}"
				val legacyId = when (status) {
					SoknadsStatus.Opprettet -> null
					SoknadsStatus.Utfylt -> ""
					else -> "12345678901"
				}
				insertLegacy(submissionId, displayType, status, legacyId)
				if (status == SoknadsStatus.Innsendt) {
					jdbc.update(
						"UPDATE soknad SET affecteduser = CAST(? AS jsonb), avsender = CAST(? AS jsonb), grantuserdigitalaccess = ? WHERE innsendingsid = ?",
						"""{"id":"10987654321","idType":"FNR"}""",
						"""{"id":"12345678901","idType":"FNR"}""",
						true,
						submissionId,
					)
				}
			}
		}
		val originalRows = legacyRowSnapshots()
		assertEquals(PERSISTED_DISPLAY_TYPES.size * SoknadsStatus.values().size, originalRows.size)

		migrate("1.44")

		assertEquals(originalRows, legacyRowSnapshots())
		assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM soknad WHERE owner IS NOT NULL", Long::class.java))
	}

	@Test
	fun `old writers can insert and update without owner after the additive migration`() {
		migrate("1.44")
		PERSISTED_DISPLAY_TYPES.values.forEach { displayType ->
			SoknadsStatus.values().forEach { status ->
				val submissionId = "inserted-$displayType-${status.name}"
				insertLegacy(submissionId, displayType, status)
				assertNull(owner(submissionId))
			}
			val submissionId = "inserted-$displayType-Opprettet"
			assertEquals(1, jdbc.update(
				"UPDATE soknad SET tittel = ?, brukerid = ?, status = ?, visningssteg = ?, endretdato = now() WHERE innsendingsid = ?",
				"Updated by an old writer", "10987654321", SoknadsStatus.Utfylt.name, 2, submissionId,
			))
			val updated = jdbc.queryForMap(
				"SELECT tittel, brukerid, status, visningssteg, visningstype, owner FROM soknad WHERE innsendingsid = ?",
				submissionId,
			)
			assertEquals("Updated by an old writer", updated["tittel"])
			assertEquals("10987654321", updated["brukerid"])
			assertEquals(SoknadsStatus.Utfylt.name, updated["status"])
			assertEquals(2, updated["visningssteg"])
			assertEquals(displayType, updated["visningstype"])
			assertNull(updated["owner"])
		}
		assertEquals(
			PERSISTED_DISPLAY_TYPES.size.toLong() * SoknadsStatus.values().size,
			jdbc.queryForObject("SELECT count(*) FROM soknad", Long::class.java),
		)
		assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM soknad WHERE owner IS NOT NULL", Long::class.java))
	}

	@Test
	fun `old style updates preserve owners written by new writers`() {
		migrate("1.44")
		val ownerJson = """{"id":"12345678901","idType":"FNR"}"""
		PERSISTED_DISPLAY_TYPES.values.forEach { displayType ->
			val submissionId = "owned-$displayType"
			jdbc.update(
				"INSERT INTO soknad (innsendingsid, visningstype, brukerid, status, owner) VALUES (?, ?, ?, ?, CAST(? AS jsonb))",
				submissionId, displayType, "12345678901", SoknadsStatus.Opprettet.name, ownerJson,
			)
			val originalOwner = assertNotNull(owner(submissionId))
			assertEquals(1, jdbc.update(
				"UPDATE soknad SET tittel = ?, brukerid = ?, status = ?, endretdato = now() WHERE innsendingsid = ?",
				"Updated by an old writer", "10987654321", SoknadsStatus.Utfylt.name, submissionId,
			))
			assertEquals(originalOwner, owner(submissionId))
		}
	}

	@Test
	fun `SQL null and JSON null remain distinct after database round trip`() {
		migrate("1.44")
		insertLegacy("sql-null", "0")
		jdbc.update(
			"INSERT INTO soknad (innsendingsid, visningstype, status, owner) VALUES (?, ?, ?, CAST(? AS jsonb))",
			"json-null", "0", SoknadsStatus.Opprettet.name, "null",
		)
		assertEquals(true, jdbc.queryForObject("SELECT owner IS NULL FROM soknad WHERE innsendingsid = ?", Boolean::class.java, "sql-null"))
		assertEquals(false, jdbc.queryForObject("SELECT owner IS NULL FROM soknad WHERE innsendingsid = ?", Boolean::class.java, "json-null"))
		assertEquals("null", owner("json-null"))
		val converter = ApplicationOwnerConverter(jacksonObjectMapper())
		assertNull(converter.convertToEntityAttribute(owner("sql-null")))
		val malformedOwner = assertNotNull(converter.convertToEntityAttribute(owner("json-null")))
		assertNull(malformedOwner.id)
		assertNull(malformedOwner.idType)
	}

	private fun migrate(target: String) {
		Flyway.configure().dataSource(dataSource).defaultSchema(schemaName).schemas(schemaName)
			.locations("classpath:db/migration").target(target).load().migrate()
	}

	private fun insertLegacy(
		submissionId: String,
		displayType: String,
		status: SoknadsStatus = SoknadsStatus.Opprettet,
		legacyId: String? = "12345678901",
	) {
		jdbc.update(
			"INSERT INTO soknad (innsendingsid, visningstype, brukerid, status, tittel) VALUES (?, ?, ?, ?, ?)",
			submissionId, displayType, legacyId, status.name, "Original title",
		)
	}

	private fun legacyRowSnapshots(): List<String> =
		jdbc.queryForList("SELECT (to_jsonb(s) - 'owner')::text FROM soknad s ORDER BY id", String::class.java)
			.map { checkNotNull(it) }

	private fun owner(submissionId: String): String? =
		jdbc.queryForObject("SELECT owner::text FROM soknad WHERE innsendingsid = ?", String::class.java, submissionId)

	companion object {
		// These are the ordinal strings persisted by existing writers, not enum names.
		private val PERSISTED_DISPLAY_TYPES = mapOf(
			"fyllUt" to "0",
			"dokumentinnsending" to "1",
			"ettersending" to "2",
			"lospost" to "3",
			"nologin" to "4",
		)
	}
}
