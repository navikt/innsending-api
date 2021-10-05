package no.nav.soknad.innsending.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface VedleggRepository: JpaRepository<VedleggDbData, Long> {

    @Query(value = "FROM VedleggDbData WHERE soknadsid = :soknadsid")
    fun findAllBySoknadsid(@Param("soknadsid") soknadsid: Long): List<VedleggDbData>

    @Query(value = "FROM VedleggDbData WHERE id = :vedleggsid")
    fun findByVedleggsid(@Param("vedleggsid") vedleggsid: Long): VedleggDbData
}