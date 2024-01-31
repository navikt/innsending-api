package no.nav.soknad.innsending.security

import no.nav.soknad.innsending.consumerapis.pdl.PdlInterface
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.util.models.kanGjoreEndringer
import no.nav.soknad.innsending.util.testpersonid
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class Tilgangskontroll(
	val subjectHandler: SubjectHandlerInterface,
	val pdlService: PdlInterface
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun hentBrukerFraToken(): String {
		return try {
			subjectHandler.getUserIdFromToken()
		} catch (ex: Exception) {
			logger.warn("Midlertidig bruk av testpersonid $testpersonid fordi følgende feil ved hentBrukerFraToken ${ex.message}")
			testpersonid
		}
	}

	fun hentPersonIdents(brukerId: String): List<String> {
		return pdlService.hentPersonIdents(brukerId).map { it.ident }
	}

	fun hentPersonIdents(): List<String> {
		val brukerId = hentBrukerFraToken()
		return pdlService.hentPersonIdents(brukerId).map { it.ident }
	}

	fun harTilgang(soknadDto: DokumentSoknadDto?) {
		val brukerId = hentBrukerFraToken()
		harTilgang(soknadDto, brukerId)
	}

	fun harTilgang(soknadDto: DokumentSoknadDto?, brukerId: String?): Boolean {
		if (soknadDto == null) {
			logger.info("Bruker forsøker å hente soknad som ikke finnes i databasen")
			throw ResourceNotFoundException("Søknad finnes ikke eller er ikke tilgjengelig for innlogget bruker")
		}

		val idents = hentPersonIdents(hentBrukerFraToken())
		if (idents.contains(soknadDto.brukerId)) return true

		logger.info("Bruker har ikke tilgang til soknad ${soknadDto.innsendingsId}")
		throw ResourceNotFoundException("Søknad finnes ikke eller er ikke tilgjengelig for innlogget bruker") //FIXME: Bytte til 403?
	}

	fun validerSoknadsTilgang(dokumentSoknadDto: DokumentSoknadDto) {
		harTilgang(dokumentSoknadDto)
		if (!dokumentSoknadDto.kanGjoreEndringer) {
			throw IllegalActionException(
				message = "Søknaden kan ikke vises. Søknaden er slettet eller innsendt og kan ikke vises eller endres.",
				errorCode = ErrorCode.APPLICATION_SENT_IN_OR_DELETED
			)
		}
	}

}
