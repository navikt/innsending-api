package no.nav.soknad.innsending.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface AliveRepository : JpaRepository<AliveDbData, Long> {

	@Query(value = "SELECT test FROM alive WHERE id in (:id)", nativeQuery = true)
	fun findTestById(@Param("id") id: Long): String

}
