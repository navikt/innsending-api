package no.nav.soknad.innsending.utils

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.soknad.innsending.model.AvsenderDto
import no.nav.soknad.innsending.model.BrukerDto
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.ApplicationOwner
import no.nav.soknad.innsending.repository.domain.utils.ApplicationOwnerConverter
import no.nav.soknad.innsending.repository.domain.utils.AvsenderDtoConverter
import no.nav.soknad.innsending.repository.domain.utils.BrukerDtoConverter
import no.nav.soknad.innsending.repository.domain.utils.SoknadsStatusAttributeConverter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional

@Transactional
class ApplicationTestRepository(
	private val jdbcTemplate: JdbcTemplate,
	objectMapper: ObjectMapper,
) {
	private val ownerConverter = ApplicationOwnerConverter(objectMapper)
	private val userConverter = BrukerDtoConverter(objectMapper)
	private val senderConverter = AvsenderDtoConverter(objectMapper)
	private val statusConverter = SoknadsStatusAttributeConverter()

	fun deleteApplications(submissionIds: List<String>) {
		submissionIds.forEach { id ->
			jdbcTemplate.update("DELETE FROM fil WHERE vedleggsid IN (SELECT id FROM vedlegg WHERE soknadsid = (SELECT id FROM soknad WHERE innsendingsid = ?))", id)
			jdbcTemplate.update("DELETE FROM vedlegg WHERE soknadsid = (SELECT id FROM soknad WHERE innsendingsid = ?)", id)
			jdbcTemplate.update("DELETE FROM hendelse WHERE innsendingsid = ?", id)
			jdbcTemplate.update("DELETE FROM soknad WHERE innsendingsid = ?", id)
		}
	}

	fun setOwner(submissionId: String, owner: ApplicationOwner?) {
		val updatedRows = jdbcTemplate.update(
			"UPDATE soknad SET owner = CAST(? AS jsonb) WHERE innsendingsid = ?",
			ownerConverter.convertToDatabaseColumn(owner),
			submissionId,
		)
		check(updatedRows == 1) { "Expected exactly one application to update" }
	}

	fun findBySubmissionId(submissionId: String): StoredApplication {
		val applications = jdbcTemplate.query(
			"""SELECT owner, owner IS NULL AS owner_is_sql_null, status, brukerid,
				affecteduser, affecteduser IS NULL AS affected_user_is_sql_null, avsender, grantuserdigitalaccess
				FROM soknad WHERE innsendingsid = ?""",
			{ row, _ ->
				StoredApplication(
					owner = ownerConverter.convertToEntityAttribute(row.getString("owner")),
					ownerIsSqlNull = row.getBoolean("owner_is_sql_null"),
					status = checkNotNull(statusConverter.convertToEntityAttribute(row.getString("status"))) {
						"Expected a stored application status"
					},
					userId = row.getString("brukerid"),
					affectedUser = userConverter.convertToEntityAttribute(row.getString("affecteduser")),
					affectedUserIsSqlNull = row.getBoolean("affected_user_is_sql_null"),
					sender = senderConverter.convertToEntityAttribute(row.getString("avsender")),
					grantUserDigitalAccess = row.getObject("grantuserdigitalaccess", Boolean::class.javaObjectType),
				)
			},
			submissionId,
		)
		return checkNotNull(applications.singleOrNull()) { "Expected exactly one stored application" }
	}
}

data class StoredApplication(
	val owner: ApplicationOwner?,
	val ownerIsSqlNull: Boolean,
	val status: SoknadsStatus,
	val userId: String?,
	val affectedUser: BrukerDto?,
	val affectedUserIsSqlNull: Boolean,
	val sender: AvsenderDto?,
	val grantUserDigitalAccess: Boolean?,
) {
	override fun toString(): String =
		"StoredApplication(owner=***, ownerIsSqlNull=$ownerIsSqlNull, status=$status, " +
			"userId=***, affectedUser=***, affectedUserIsSqlNull=$affectedUserIsSqlNull, " +
			"sender=***, grantUserDigitalAccess=$grantUserDigitalAccess)"
}
