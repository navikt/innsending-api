package no.nav.soknad.innsending.repository

import no.nav.soknad.innsending.repository.domain.models.FilDbWithoutFileData
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface FilWithoutDataRepository : JpaRepository<FilDbWithoutFileData, Long> {

	@Query(value = "FROM FilDbWithoutFileData WHERE vedleggsid = :vedleggsid order by id")
	fun findFilDbWIthoutFileDataByVedleggsid(@Param("vedleggsid") vedleggsid: Long): List<FilDbWithoutFileData>

}
