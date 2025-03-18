package no.nav.soknad.innsending.repository

import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.FilDbData
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Repository
interface FilRepository : JpaRepository<FilDbData, Long> {

	@Query(value = "FROM FilDbData WHERE vedleggsid = :vedleggsid order by id")
	fun findAllByVedleggsid(@Param("vedleggsid") vedleggsid: Long): List<FilDbData>

	@Query(value = "FROM FilDbData WHERE vedleggsid = :vedleggsid and id = :id")
	fun findByVedleggsidAndId(@Param("vedleggsid") vedleggsid: Long, @Param("id") id: Long): FilDbData?

	@Query(value = "SELECT sum(storrelse) FROM FilDbData WHERE vedleggsid = :vedleggsid")
	fun findSumByVedleggsid(@Param("vedleggsid") vedleggsid: Long): Long?

	@Transactional
	@Modifying
	@Query(value = "DELETE FROM fil WHERE vedleggsid = :vedleggsid and id = :id", nativeQuery = true)
	fun deleteByVedleggsidAndId(@Param("vedleggsid") vedleggsid: Long, @Param("id") id: Long)

	@Transactional
	@Modifying
	@Query(value = "DELETE FROM fil WHERE vedleggsid = :vedleggsid", nativeQuery = true)
	fun deleteAllByVedleggsid(@Param("vedleggsid") vedleggsid: Long)

	@Query(value = "SELECT sum(pg_database_size(pg_database.datname)) FROM pg_database", nativeQuery = true)
	fun totalDbSize(): Long
}
