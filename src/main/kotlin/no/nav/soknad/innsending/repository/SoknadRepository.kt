package no.nav.soknad.innsending.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SoknadRepository : JpaRepository<SoknadDbData, Long> {

	fun findByInnsendingsid(innsendingsid: String): Optional<SoknadDbData>
}
