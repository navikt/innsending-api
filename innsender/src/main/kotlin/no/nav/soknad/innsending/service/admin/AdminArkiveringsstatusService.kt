package no.nav.soknad.innsending.service.admin

import no.nav.soknad.innsending.exceptions.ConflictException
import no.nav.soknad.innsending.exceptions.ForbiddenException
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.service.RepositoryUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class AdminArkiveringsstatusService(
	private val repo: RepositoryUtils,
	@Value("\${NAIS_CLUSTER_NAME:}") private val clusterName: String,
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	fun verifiserTilgang(azpName: String?) {
		if (clusterName != TILLATT_CLUSTER) {
			logger.warn("Avviste endring av arkiveringsstatus: ikke tillatt i cluster '${sanitize(clusterName)}'")
			throw ForbiddenException("Endring av arkiveringsstatus er kun tillatt i $TILLATT_CLUSTER")
		}
		if (azpName != TILLATT_KLIENT) {
			logger.warn("Avviste endring av arkiveringsstatus: kallende applikasjon '${sanitize(azpName ?: "")}' har ikke tilgang")
			throw ForbiddenException("Kallende applikasjon har ikke tilgang til å endre arkiveringsstatus")
		}
	}

	fun settArkivert(innsendingsId: String, begrunnelse: String, navIdent: String) {
		val renBegrunnelse = sanitize(begrunnelse).trim()
		require(renBegrunnelse.isNotBlank()) { "Begrunnelse må fylles ut" }
		require(begrunnelse.length <= MAKS_LENGDE_BEGRUNNELSE) { "Begrunnelse kan maks være $MAKS_LENGDE_BEGRUNNELSE tegn" }

		val soknad = repo.hentSoknadDb(innsendingsId)
		if (soknad.arkiveringsstatus != ArkiveringsStatus.ArkiveringFeilet) {
			logger.warn(
				"Admin user ${sanitize(navIdent)} attempted to change arkiveringsstatus for innsendingsId=${soknad.innsendingsid}, " +
					"but current status is ${soknad.arkiveringsstatus}"
			)
			throw ConflictException(
				"Søknad har arkiveringsstatus ${soknad.arkiveringsstatus}, kan kun endre fra ${ArkiveringsStatus.ArkiveringFeilet}"
			)
		}

		repo.oppdaterArkiveringsstatus(soknad, ArkiveringsStatus.Arkivert)
		logger.info(
			"Admin user ${sanitize(navIdent)} changed arkiveringsstatus for innsendingsId=${soknad.innsendingsid} " +
				"from ${ArkiveringsStatus.ArkiveringFeilet} to ${ArkiveringsStatus.Arkivert}. Begrunnelse: $renBegrunnelse"
		)
	}

	private fun sanitize(value: String): String = value.replace(CONTROL_CHARS, " ")

	companion object {
		const val TILLATT_CLUSTER = "dev-gcp"
		const val TILLATT_KLIENT = "dev-gcp:team-soknad:innsending-admin"
		const val MAKS_LENGDE_BEGRUNNELSE = 200
		private val CONTROL_CHARS = Regex("\\p{Cntrl}")
	}
}
