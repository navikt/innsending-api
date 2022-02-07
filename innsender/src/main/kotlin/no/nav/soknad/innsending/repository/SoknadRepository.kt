package no.nav.soknad.innsending.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
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

}
