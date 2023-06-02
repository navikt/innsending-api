package no.nav.soknad.innsending.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface HendelseRepository : JpaRepository<HendelseDbData, Long> {

	fun findAllByInnsendingsidOrderByTidspunkt(innsendingsId: String): List<HendelseDbData>

	fun countByHendelsetype(hendelseType: HendelseType): Long?
}
