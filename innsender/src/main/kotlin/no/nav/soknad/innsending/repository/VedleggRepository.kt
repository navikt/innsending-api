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
interface VedleggRepository : JpaRepository<VedleggDbData, Long> {

	@Query(value = "FROM VedleggDbData WHERE soknadsid = :soknadsid order by id")
	fun findAllBySoknadsid(@Param("soknadsid") soknadsid: Long): List<VedleggDbData>

	@Query(value = "FROM VedleggDbData WHERE id = :vedleggsid")
	fun findByVedleggsid(@Param("vedleggsid") vedleggsid: Long): Optional<VedleggDbData>

	@Transactional
	@Modifying
	@Query(value="UPDATE VedleggDbData v SET v.status = :status, v.endretdato = :endretdato WHERE v.id = :id")
	fun updateStatus(@Param("id") id: Long, @Param("status") status: OpplastingsStatus, @Param("endretdato") endretdato: LocalDateTime)

	@Transactional
	@Modifying
	@Query(value="UPDATE VedleggDbData v SET v.status = :status, v.endretdato = :endretdato, v.innsendtdato = :innsendtdato WHERE v.id = :id")
	fun updateStatusAndInnsendtdato(@Param("id") id: Long, @Param("status") status: OpplastingsStatus, @Param("endretdato") endretdato: LocalDateTime, @Param("innsendtdato") innsendtdato: LocalDateTime)

	@Transactional
	@Modifying
	@Query(value="UPDATE VedleggDbData v SET v.tittel = :tittel, v.label= :tittel, v.status= :status, v.endretdato = :endretdato WHERE v.id = :id")
	fun patchVedlegg(@Param("id") id: Long, @Param("tittel") tittel: String, @Param("status") status: OpplastingsStatus, @Param("endretdato") endretdato: LocalDateTime)

}
