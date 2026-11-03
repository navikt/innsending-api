package no.nav.soknad.innsending.repository

import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.sql.SQLException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OwnerBackfillMigrationTest {

	private val databasePassword = UUID.randomUUID().toString()
	private val postgres = GenericContainer<Nothing>("postgres:18.3").apply {
		withEnv("POSTGRES_DB", "owner_migration")
		withEnv("POSTGRES_USER", "migration_test")
		withEnv("POSTGRES_PASSWORD", databasePassword)
		withExposedPorts(5432)
		waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))
	}
	private lateinit var schemaName: String
	private lateinit var dataSource: PGSimpleDataSource
	private lateinit var jdbc: JdbcTemplate

	@BeforeAll
	fun startDatabase() {
		postgres.start()
	}

	@AfterAll
	fun stopDatabase() {
		postgres.stop()
	}

	@BeforeEach
	fun migrateIsolatedSchemaToReleaseOne() {
		schemaName = "owner_backfill_" + UUID.randomUUID().toString().replace("-", "")
		dataSource = PGSimpleDataSource().apply {
			setURL("jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/owner_migration")
			user = "migration_test"
			password = databasePassword
		}
		// This schema identifier is generated locally, never supplied by a caller.
		JdbcTemplate(dataSource).execute("CREATE SCHEMA $schemaName")
		dataSource.currentSchema = schemaName
		jdbc = JdbcTemplate(dataSource)
		migrate("1.44")
	}

	@AfterEach
	fun removeIsolatedSchema() {
		jdbc.execute("DROP SCHEMA $schemaName CASCADE")
	}

	@Test
	fun `release two backfills old writer rows across every display type and status and migrates once`() {
		// Release one has the nullable column but old writers still omit it.
		(0..4).forEach { displayType ->
			SoknadsStatus.values().forEach { status ->
				val submissionId = "legacy-$displayType-${status.name}"
				insert(submissionId, displayType.toString(), status = status.name)
				jdbc.update(
					"UPDATE soknad SET tittel = ?, brukerid = ?, brukertype = ?, status = ? WHERE innsendingsid = ?",
					"Updated by an old writer", LEGACY_ID, if (displayType % 2 == 0) null else "0", status.name, submissionId,
				)
			}
		}
		val legacyState = snapshots(" - 'owner' - 'affecteduser'")

		assertEquals(2, migrate("1.46"))

		assertEquals(legacyState, snapshots(" - 'owner' - 'affecteduser'"))
		assertEquals(4L * SoknadsStatus.values().size, count("owner IS NOT NULL"))
		assertEquals(0L, count("visningstype = '4' AND owner IS NOT NULL"))
		assertEquals(0L, count("visningstype <> '4' AND owner IS DISTINCT FROM jsonb_build_object('id', brukerid, 'idType', 'FNR')"))
		assertEquals(0L, count("affecteduser IS DISTINCT FROM jsonb_build_object('id', brukerid, 'idType', 'FNR')"))
		val migratedState = snapshots()
		assertEquals(0, migrate("1.46"))
		assertEquals(migratedState, snapshots())

		val indexDefinition = jdbc.queryForObject(
			"SELECT pg_get_indexdef(indexrelid) FROM pg_index WHERE indexrelid = 'soknad_owner_status_idx'::regclass AND indisvalid",
			String::class.java,
		).orEmpty()
		assertTrue(indexDefinition.contains("USING btree"))
		assertTrue(indexDefinition.contains("owner ->> 'idType'"))
		assertTrue(indexDefinition.contains("owner ->> 'id'"))
		assertTrue(indexDefinition.contains("status) WHERE"))
		assertTrue(indexDefinition.contains("visningstype"))
		assertFalse(indexDefinition.contains("'4'"))
	}

	@Test
	fun `an existing owner is preserved when the historical affected party must be filled`() {
		insert("existing-owner", owner = OWNER)
		val originalState = snapshots(" - 'affecteduser'")

		migrate("1.46")

		assertEquals(originalState, snapshots(" - 'affecteduser'"))
		assertEquals(0L, count("affecteduser IS DISTINCT FROM jsonb_build_object('id', brukerid, 'idType', 'FNR')"))
	}

	@Test
	fun `existing owners and distinct digital parties are preserved while anonymous drafts stay ownerless`() {
		insert("digital", owner = OWNER, affectedUser = OTHER_PARTY)
		insert("anonymous-legacy", displayType = "4", affectedUser = OWNER)
		insert("anonymous-party", displayType = "4", legacyId = null, affectedUser = OTHER_PARTY)
		insert("anonymous-draft", displayType = "4", legacyId = null)
		val originalState = snapshots()

		migrate("1.46")

		assertEquals(originalState, snapshots())
		assertEquals(OWNER_ID, jdbc.queryForObject("SELECT owner ->> 'id' FROM soknad WHERE innsendingsid = 'digital'", String::class.java))
		assertEquals(OTHER_ID, jdbc.queryForObject("SELECT affecteduser ->> 'id' FROM soknad WHERE innsendingsid = 'digital'", String::class.java))
		assertEquals(0L, count("visningstype = '4' AND owner IS NOT NULL"))
		assertNull(jdbc.queryForObject("SELECT affecteduser::text FROM soknad WHERE innsendingsid = 'anonymous-draft'", String::class.java))
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("conflicts")
	fun `conflicts abort the actual Flyway transaction without partial changes`(
		description: String,
		displayType: String?,
		legacyId: String?,
		userType: String?,
		owner: String?,
		affectedUser: String?,
	) {
		insert("valid-digital")
		insert("valid-anonymous", displayType = "4")
		insert("conflict", displayType, legacyId, userType, owner, affectedUser)
		val originalState = snapshots()

		val failure = assertFailsWith<FlywayException>(description) { migrate("1.46") }

		assertEquals(originalState, snapshots())
		assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version IN ('1.45', '1.46')", Long::class.java))
		val databaseError = generateSequence<Throwable>(failure) { it.cause }
			.filterIsInstance<SQLException>().last().message.orEmpty()
		assertTrue(Regex("Owner backfill rejected: invalid_rows=1").containsMatchIn(databaseError))
		assertFalse(databaseError.contains(LEGACY_ID))
		assertFalse(databaseError.contains(OTHER_ID))
		assertFalse(databaseError.contains("{\""))
	}

	private fun migrate(target: String): Int =
		Flyway.configure().dataSource(dataSource).defaultSchema(schemaName).schemas(schemaName)
			.locations("classpath:db/migration").target(target).load().migrate().migrationsExecuted

	private fun insert(
		submissionId: String,
		displayType: String? = "0",
		legacyId: String? = LEGACY_ID,
		userType: String? = null,
		owner: String? = null,
		affectedUser: String? = null,
		status: String = "Opprettet",
	) {
		jdbc.update(
			"INSERT INTO soknad (innsendingsid, visningstype, brukerid, brukertype, owner, affecteduser, status) VALUES (?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?)",
			submissionId, displayType, legacyId, userType, owner, affectedUser, status,
		)
	}

	private fun snapshots(excludedFields: String = ""): List<String> =
		// excludedFields is a test-local SQL constant, never external input.
		jdbc.queryForList("SELECT (to_jsonb(s)$excludedFields)::text FROM soknad s ORDER BY id", String::class.java).map { checkNotNull(it) }

	private fun count(predicate: String): Long? =
		// predicate is a test-local SQL constant, never external input.
		jdbc.queryForObject("SELECT count(*) FROM soknad WHERE $predicate", Long::class.java)

	companion object {
		private const val LEGACY_ID = "12345678901"
		private const val OWNER_ID = LEGACY_ID
		private const val OTHER_ID = "10987654321"
		private const val OWNER = """{"id":"12345678901","idType":"FNR"}"""
		private const val OTHER_PARTY = """{"id":"10987654321","idType":"FNR"}"""

		@JvmStatic
		fun conflicts(): List<Arguments> = buildList {
			fun conflict(
				description: String,
				displayType: String? = "0",
				legacyId: String? = LEGACY_ID,
				userType: String? = null,
				owner: String? = null,
				affectedUser: String? = null,
			) { add(Arguments.of(description, displayType, legacyId, userType, owner, affectedUser)) }
			conflict("unknown display type", displayType = "5")
			conflict("enum name is not a persisted ordinal", displayType = "fyllUt")
			conflict("missing display type", displayType = null)
			conflict("missing digital legacy identity", legacyId = null)
			conflict("empty legacy identity", legacyId = "")
			conflict("short legacy identity", legacyId = "1234567890")
			conflict("non-ASCII legacy identity", legacyId = "１２３４５６７８９０１")
			conflict("non-FNR legacy type", userType = "1")
			conflict("enum name is not a legacy type ordinal", userType = "FNR")
			conflict("existing owner conflicts with legacy identity", owner = OTHER_PARTY)
			listOf(
				"null", "[]", "\"text\"", "{}",
				"""{"id":"12345678901"}""",
				"""{"id":"12345678901","other":"FNR"}""",
				"""{"id":"12345678901","idType":"FNR","extra":true}""",
				"""{"id":12345678901,"idType":"FNR"}""",
				"""{"id":"12345678901","idType":0}""",
				"""{"id":null,"idType":"FNR"}""",
				"""{"id":"12345678901","idType":null}""",
				"""{"id":"12345678901","idType":"ORGNR"}""",
				"""{"id":"1234567890","idType":"FNR"}""",
				"""{"id":"１２３４５６７８９０１","idType":"FNR"}""",
			).forEachIndexed { index, json ->
				conflict("malformed owner $index", owner = json)
				conflict("malformed affected party $index", affectedUser = json)
			}
			conflict("anonymous owner must be SQL null", displayType = "4", owner = OWNER)
			conflict("anonymous JSON null owner is not SQL null", displayType = "4", owner = "null")
			conflict("anonymous affected party conflicts with legacy", displayType = "4", affectedUser = OTHER_PARTY)
			conflict("invalid anonymous legacy identity", displayType = "4", legacyId = "1234567890")
			conflict("invalid anonymous legacy type", displayType = "4", userType = "1")
		}
	}
}
