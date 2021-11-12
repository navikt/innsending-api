package no.nav.soknad.innsending.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface FilRepository: JpaRepository<FilDbData, Long> {

	@Query(value = "FROM FilDbData WHERE vedleggsid = :vedleggsid")
	fun findAllByVedleggsid(@Param("vedleggsid") vedleggsid: Long): List<FilDbData>

	@Query(value = "SELECT count(*) FROM fil WHERE vedleggsid = :vedleggsid", nativeQuery = true)
	fun findNumberOfFilesByVedleggsid(@Param("vedleggsid") vedleggsid: Long): Int

	@Transactional
	@Modifying
	@Query(value = "DELETE FROM fil WHERE vedleggsid = :vedleggsid", nativeQuery = true)
	fun deleteFilDbDataForVedlegg(@Param("vedleggsid") vedleggsid: Long)

}
