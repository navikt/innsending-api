package no.nav.soknad.innsending.repository

import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.SoknadDbData
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

@Repository
interface SoknadRepository : JpaRepository<SoknadDbData, Long> {

	fun findByInnsendingsid(innsendingsid: String): SoknadDbData?

	@Query(
		value = "SELECT * FROM soknad WHERE innsendtdato is not null AND (innsendingsid = :ettersendingsid OR (ettersendingsid is not null AND ettersendingsid = :ettersendingsid)) ORDER BY innsendtdato DESC",
		nativeQuery = true
	)
	fun findNewestByEttersendingsId(@Param("ettersendingsid") ettersendingsid: String): List<SoknadDbData>

	@Query(
		value = "SELECT * FROM soknad WHERE brukerid in (:brukerids) AND status in (:status) ORDER BY endretdato DESC",
		nativeQuery = true
	)
	fun findSoknadDbByAllBrukerIdAndStatus(
		@Param("brukerids") brukerids: String,
		@Param("status") status: String
	): List<SoknadDbData>

	fun findByBrukeridAndStatus(brukerid: String, status: SoknadsStatus): List<SoknadDbData>

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

	@Query(value = "SELECT * FROM soknad WHERE opprettetdato <= :opprettetFor ORDER BY opprettetdato", nativeQuery = true)
	fun findAllByOpprettetdatoBefore(@Param("opprettetFor") opprettetFor: OffsetDateTime): List<SoknadDbData>

	@Query(
		value = "SELECT * FROM soknad WHERE arkiveringsstatus <> 'Arkivert' AND innsendtdato >= :start AND innsendtdato <= :end ORDER BY innsendtdato",
		nativeQuery = true
	)
	fun findAllNotArchivedAndInnsendtdatoBetween(
		@Param("start") start: LocalDateTime,
		@Param("end") end: LocalDateTime
	): List<SoknadDbData>

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

	@Query(
		value = "SELECT COUNT(*) FROM soknad WHERE arkiveringsstatus = 'Arkivert' AND status = 'Innsendt'",
		nativeQuery = true
	)
	fun countErArkivert(): Long

	@Query(
		value = "SELECT COUNT(*) FROM soknad WHERE arkiveringsstatus = 'ArkiveringFeilet' AND status  = 'Innsendt'",
		nativeQuery = true
	)
	fun countArkiveringFeilet(): Long

	@Query(
		value = "SELECT COUNT(*) FROM soknad WHERE arkiveringsstatus = 'IkkeSatt' AND status  = 'Innsendt' AND innsendtdato < :before",
		nativeQuery = true
	)
	fun countInnsendtIkkeBehandlet(@Param("before") before: LocalDateTime): Long

	@Query(
		value = "SELECT innsendingsid FROM soknad WHERE arkiveringsstatus = 'IkkeSatt' AND status  = 'Innsendt' AND innsendtdato < :before",
		nativeQuery = true
	)
	fun findInnsendtAndArkiveringsStatusIkkeSatt(@Param("before") before: LocalDateTime): List<String>

	@Transactional
	@Modifying
	@Query(
		value = "SELECT * FROM soknad WHERE " +
			"  status = :status and arkiveringsstatus = :arkiveringsstatus and" +
			"  innsendtdato between :fra and :til",
		nativeQuery = true
	)
	fun finnAlleSoknaderBySoknadsstatusAndArkiveringsstatusAndBetweenInnsendtdatos(
		@Param("fra") fra: LocalDateTime,
		@Param("til") til: LocalDateTime,
		@Param("status") status: String = SoknadsStatus.Innsendt.name,
		@Param("arkiveringsstatus") arkiveringsstatus: String = ArkiveringsStatus.Arkivert.name
	): List<SoknadDbData>

}
