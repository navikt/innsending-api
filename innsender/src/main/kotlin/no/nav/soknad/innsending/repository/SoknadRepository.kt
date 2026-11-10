package no.nav.soknad.innsending.repository

import no.nav.soknad.innsending.model.AvsenderDto
import no.nav.soknad.innsending.model.BrukerDto
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.SoknadDbData
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.OffsetDateTime

@Repository
interface SoknadRepository : JpaRepository<SoknadDbData, Long> {

	fun findByInnsendingsid(innsendingsid: String): SoknadDbData?
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM SoknadDbData s WHERE s.innsendingsid = :innsendingsid")
	fun findByInnsendingsidForUpdate(@Param("innsendingsid") innsendingsid: String): SoknadDbData?
	fun existsByInnsendingsid(innsendingsid: String): Boolean
	@Query(
		value = """SELECT * FROM soknad WHERE status = :status
			AND visningstype IN ('0', '1', '2', '3')
			AND jsonb_typeof(owner) = 'object'
			AND CASE WHEN jsonb_typeof(owner) = 'object' THEN owner - 'id' - 'idType' END = '{}'::jsonb
			AND jsonb_typeof(owner->'id') = 'string'
			AND jsonb_typeof(owner->'idType') = 'string'
			AND owner->>'idType' = 'FNR' AND owner->>'id' ~ '^[0-9]{11}$'
			AND owner->>'id' IN (:idents)""",
		nativeQuery = true,
	)
	fun findByOwnerAndStatus(@Param("idents") idents: List<String>, @Param("status") status: String): List<SoknadDbData>

	// This is a task-API lookup, never a person-access lookup.
	@Query(
		value = """SELECT * FROM soknad WHERE status = :status
			AND visningstype IN ('0', '1', '2', '3')
			AND affecteduser->>'idType' = 'FNR' AND affecteduser->>'id' IN (:idents)
			AND affecteduser->>'id' ~ '^[0-9]{11}$'
			AND affecteduser = jsonb_build_object('id', affecteduser->>'id', 'idType', 'FNR')""",
		nativeQuery = true,
	)
	fun findByAffectedPersonAndStatus(@Param("idents") idents: List<String>, @Param("status") status: String): List<SoknadDbData>

	@Transactional
	@Modifying
	@Query(value = "UPDATE SoknadDbData SET endretdato = :endretdato WHERE id = :id", nativeQuery = false)
	fun updateEndretDato(@Param("id") id: Long, @Param("endretdato") endretdato: LocalDateTime)

	@Transactional
	@Modifying
	@Query(
		value = "UPDATE SoknadDbData SET endretdato = :endretdato, visningssteg = :visningssteg WHERE id = :id",
		nativeQuery = false
	)
	fun updateVisningsStegAndEndretDato(
		@Param("id") id: Long,
		@Param("visningssteg") visningsSteg: Long,
		@Param("endretdato") endretdato: LocalDateTime
	)

	@Query(
		value = "SELECT * FROM soknad WHERE status IN (:statuses) AND opprettetdato <= :opprettetFor ORDER BY opprettetdato",
		nativeQuery = true
	)
	fun findAllByStatusesAndWithOpprettetdatoBefore(
		@Param("statuses") statuses: List<String>,
		@Param("opprettetFor") opprettetFor: OffsetDateTime
	): List<SoknadDbData>

	@Query(
		value = "SELECT * FROM soknad WHERE status IN (:statuses) AND skalslettesdato <= :date ORDER BY skalslettesdato",
		nativeQuery = true
	)
	fun findAllByStatusesAndWithSkalSlettesDatoBefore(
		@Param("statuses") statuses: List<String>,
		@Param("date") date: OffsetDateTime
	): List<SoknadDbData>

	@Query(value = "SELECT * FROM soknad WHERE opprettetdato <= :opprettetFor ORDER BY opprettetdato", nativeQuery = true)
	fun findAllByOpprettetdatoBefore(@Param("opprettetFor") opprettetFor: OffsetDateTime): List<SoknadDbData>

	@Transactional
	@Modifying
	@Query(
		value = "UPDATE SoknadDbData SET arkiveringsstatus = :arkiveringsStatus WHERE innsendingsid in (:innsendingsids)",
		nativeQuery = false
	)
	fun updateArkiveringsStatus(
		arkiveringsStatus: ArkiveringsStatus,
		@Param("innsendingsids") innsendingsids: List<String>
	)

	@Transactional
	@Modifying
	@Query(
		value = "UPDATE SoknadDbData SET status = :status, avsender = :avsender, affecteduser = :affecteduser WHERE innsendingsid = :innsendingsid",
		nativeQuery = false
	)
	fun updateStatusAvsenderAndAffecteduser(
		@Param("status") status: SoknadsStatus, @Param("avsender") avsender: AvsenderDto?, @Param("affecteduser") affecteduser: BrukerDto?,
		@Param("innsendingsid") innsendingsid: String
	)

	@Query(
		value = "SELECT COUNT(*) FROM soknad WHERE arkiveringsstatus = 'Arkivert' AND status = 'Innsendt'",
		nativeQuery = true
	)
	fun countErArkivert(): Long

	@Transactional
	@Modifying
	@Query(
		value = "UPDATE SoknadDbData SET tema = :tema WHERE id = :id",
		nativeQuery = false
	)
	fun updateTema(
		@Param("id") id: Long, @Param("tema") tema: String
	)

	@Query(
		value = "SELECT COUNT(*) FROM soknad WHERE arkiveringsstatus = 'ArkiveringFeilet' AND status  = 'Innsendt'",
		nativeQuery = true
	)
	fun countArkiveringFeilet(): Long

	@Query(
		value = "SELECT COUNT(*) FROM soknad WHERE arkiveringsstatus = 'IkkeSatt' AND (status  = 'Innsendt' OR status = 'KlarForInnsending') AND innsendtdato < :before",
		nativeQuery = true
	)
	fun countInnsendtIkkeBehandlet(@Param("before") before: LocalDateTime): Long

	@Query(
		value = "SELECT innsendingsid FROM soknad WHERE arkiveringsstatus = 'IkkeSatt' AND ( status  = 'Innsendt' OR status = 'KlarForInnsending') AND innsendtdato < :before",
		nativeQuery = true
	)
	fun findInnsendtAndArkiveringsStatusIkkeSatt(@Param("before") before: LocalDateTime): List<String>

	@Query(
		value = "SELECT COUNT(*) FROM soknad WHERE status = 'KlarForInnsending' AND innsendtdato < :before",
		nativeQuery = true
	)
	fun countNotSentInApplications(@Param("before") before: LocalDateTime): Long

	@Query(
		value = "SELECT innsendingsid FROM soknad WHERE status  = 'KlarForInnsending' AND innsendtdato < :before",
		nativeQuery = true
	)
	fun findNotSentIntApplications(@Param("before") before: LocalDateTime): List<String>

	@Transactional
	@Modifying
	@Query(
		value = "SELECT * FROM soknad WHERE " +
			"  status = :status and arkiveringsstatus = :arkiveringsstatus and" +
			"  innsendtdato between :fra and :til",
		nativeQuery = true
	)
	fun finnAlleSoknaderBySoknadsstatusAndArkiveringsstatusAndBetweenInnsendtdatosOrderByInnsendtdato(
		@Param("fra") fra: LocalDateTime,
		@Param("til") til: LocalDateTime,
		@Param("status") status: String = SoknadsStatus.Innsendt.name,
		@Param("arkiveringsstatus") arkiveringsstatus: String = ArkiveringsStatus.Arkivert.name
	): List<SoknadDbData>


	@Query(
		value = "SELECT COUNT(*) FROM soknad WHERE innsendtdato > :since AND visningstype = CAST(:visningsType AS VARCHAR)",
		nativeQuery = true
	)
	fun countRecentlySubmitted(visningsType: VisningsType, since: LocalDateTime): Long

}
