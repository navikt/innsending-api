package no.nav.soknad.innsending.repository

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

	fun findByInnsendingsid(innsendingsid: String): Optional<SoknadDbData>

	@Query(value = "SELECT * FROM soknad WHERE innsendtdato is not null AND (innsendingsid = :ettersendingsid OR (ettersendingsid is not null AND ettersendingsid = :ettersendingsid)) ORDER BY innsendtdato DESC", nativeQuery = true)
	fun findNewestByEttersendingsId(@Param("ettersendingsid") ettersendingsid: String): List<SoknadDbData>

	@Query(value = "SELECT * FROM soknad WHERE brukerid in (:brukerids) AND status in (:status) ORDER BY endretdato DESC", nativeQuery = true)
	fun findSoknadDbByAllBrukerIdAndStatus(@Param("brukerids") brukerids: String, @Param("status") status: String): List<SoknadDbData>

	fun findByBrukeridAndStatus(brukerid: String, status: SoknadsStatus): List<SoknadDbData>

	@Transactional
	@Modifying
	@Query(value="UPDATE SoknadDbData SET endretdato = :endretdato WHERE id = :id", nativeQuery = false)
	fun updateEndretDato(@Param("id") id: Long, @Param("endretdato") endretdato: LocalDateTime)

	@Transactional
	@Modifying
	@Query(value="UPDATE SoknadDbData SET endretdato = :endretdato, visningssteg = :visningssteg WHERE id = :id", nativeQuery = false)
	fun updateVisningsStegAndEndretDato(@Param("id") id: Long, @Param("visningssteg") visningsSteg: Long, @Param("endretdato") endretdato: LocalDateTime)

	@Query(value = "SELECT * FROM soknad WHERE status = :status AND opprettetdato <= :opprettetFor ORDER BY opprettetdato", nativeQuery = true)
	fun findAllByStatusAndWithOpprettetdatoBefore(@Param("status") status: String, @Param("opprettetFor") opprettetFor: OffsetDateTime): List<SoknadDbData>

	@Query(value = "SELECT * FROM soknad WHERE opprettetdato <= :opprettetFor ORDER BY opprettetdato", nativeQuery = true)
	fun findAllByOpprettetdatoBefore(@Param("opprettetFor") opprettetFor: OffsetDateTime): List<SoknadDbData>

	@Query(value = "SELECT * FROM soknad WHERE erarkivert IS NOT TRUE AND innsendtdato >= :start AND innsendtdato <= :end ORDER BY innsendtdato", nativeQuery = true)
	fun findAllNotArchivedAndInnsendtdatoBetween(@Param("start") start: LocalDateTime, @Param("end") end: LocalDateTime): List<SoknadDbData>

	@Transactional
	@Modifying
	@Query(value = "UPDATE SoknadDbData SET erarkivert = :erArkivert WHERE innsendingsid in (:innsendingsids)", nativeQuery = false)
	fun updateErArkivert(erArkivert: Boolean, @Param("innsendingsids") innsendingsids: List<String>)

	@Query(value = "SELECT COUNT(*) FROM soknad WHERE erarkivert = :erarkivert", nativeQuery = true)
	fun countErarkivertIs(@Param("erarkivert") erarkivert: Boolean): Long
}
