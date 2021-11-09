package no.nav.soknad.innsending.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Repository
interface VedleggRepository : JpaRepository<VedleggDbData, Long> {

	@Query(value = "FROM VedleggDbData WHERE soknadsid = :soknadsid")
	fun findAllBySoknadsid(@Param("soknadsid") soknadsid: Long): List<VedleggDbData>

	@Query(value = "FROM VedleggDbData WHERE id = :vedleggsid")
	fun findByVedleggsid(@Param("vedleggsid") vedleggsid: Long): VedleggDbData?

	@Transactional
	@Modifying
	@Query(value="UPDATE vedlegg SET status = :status, endretdato = :endretdato WHERE id = :id", nativeQuery = true)
	fun updateStatus(@Param("id") id: Long, @Param("status") status: OpplastingsStatus, @Param("endretdato") endretdato: LocalDateTime)

}
