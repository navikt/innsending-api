package no.nav.soknad.innsending.repository

import no.nav.soknad.innsending.repository.domain.models.ConfigDbData
import org.springframework.data.jpa.repository.JpaRepository

interface ConfigRepository: JpaRepository<ConfigDbData, Long> {
	fun findByKey(key: String): ConfigDbData?
	fun findAllByOrderByKey(): List<ConfigDbData>
}
