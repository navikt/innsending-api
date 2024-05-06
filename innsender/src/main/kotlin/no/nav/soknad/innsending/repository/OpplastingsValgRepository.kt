package no.nav.soknad.innsending.repository

import no.nav.soknad.innsending.repository.domain.models.VedleggVisningsRegelDbData
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface OpplastingsValgRepository: JpaRepository<VedleggVisningsRegelDbData, Long> {

	@Query(value = "FROM VedleggVisningsRegelDbData WHERE vedleggsid = :vedleggsid order by id")
	fun findAllByVedleggsid(@Param("vedleggsid") vedleggsid: Long): List<VedleggVisningsRegelDbData>

	@Transactional
	@Modifying
	@Query(value = "DELETE FROM vedleggVisningsRegel WHERE vedleggsid = :vedleggsid", nativeQuery = true)
	fun deleteAllByVedleggsid(@Param("vedleggsid") vedleggsid: Long)


}
