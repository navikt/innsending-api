package no.nav.soknad.innsending.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface HendelseRepository : JpaRepository<HendelseDbData, Long> {

	fun findAllByInnsendingsidOrderByTidspunkt(innsendingsId: String): List<HendelseDbData>

	@Query(value = "FROM HendelseDbData WHERE innsendingsid = :innsendingsId and hendelsetype = :hendelseType order by tidspunkt desc")
	fun findAllByInnsendingsidAndHendelsetypeAndOrderByTidspunktDesc(
		innsendingsId: String,
		hendelseType: HendelseType
	): List<HendelseDbData>

	fun countByHendelsetype(hendelseType: HendelseType): Long?
}
