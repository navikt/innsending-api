package no.nav.soknad.innsending.rest.ettersending


import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.SoknadsveiviserApi
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.OpprettEttersendingGittSkjemaNr
import no.nav.soknad.innsending.model.OpprettSoknadBody
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.EttersendingService
import no.nav.soknad.innsending.service.SafService
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.supervision.timer.Timed
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.Constants.MAX_AKTIVE_DAGER
import no.nav.soknad.innsending.util.finnSpraakFraInput
import no.nav.soknad.innsending.util.logging.CombinedLogger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

// Soknadsveiviser (old dokumentinnsending) creates a link to sendinn-frontend with query parameters to create a new soknad/ettersendelse
// Since soknadsveiviser is deprecated and will be removed, it is separated from the sendinn folder
@RestController
@CrossOrigin(maxAge = 3600)
@ProtectedWithClaims(
	issuer = Constants.TOKENX,
	claimMap = [Constants.CLAIM_ACR_LEVEL_4, Constants.CLAIM_ACR_IDPORTEN_LOA_HIGH],
	combineWithOr = true
)
class SoknadsveiviserRestApi(
	private val soknadService: SoknadService,
	private val tilgangskontroll: Tilgangskontroll,
	private val ettersendingService: EttersendingService,
	private val safService: SafService,
) : SoknadsveiviserApi {

	private val logger = LoggerFactory.getLogger(javaClass)
	private val secureLogger = LoggerFactory.getLogger("secureLogger")
	private val combinedLogger = CombinedLogger(logger, secureLogger)

	@Timed(InnsenderOperation.OPPRETT)
	override fun opprettSoknad(opprettSoknadBody: OpprettSoknadBody): ResponseEntity<DokumentSoknadDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		combinedLogger.log("Skal opprette søknad for ${opprettSoknadBody.skjemanr}", brukerId)

		soknadService.loggWarningVedEksisterendeSoknad(brukerId, opprettSoknadBody.skjemanr)

		val dokumentSoknadDto = soknadService.opprettSoknad(
			brukerId,
			opprettSoknadBody.skjemanr,
			finnSpraakFraInput(opprettSoknadBody.sprak),
			opprettSoknadBody.vedleggsListe ?: emptyList()
		)

		combinedLogger.log(
			"${dokumentSoknadDto.innsendingsId}: Opprettet søknad på skjema ${opprettSoknadBody.skjemanr}",
			brukerId
		)

		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(dokumentSoknadDto)
	}

	@Timed(InnsenderOperation.OPPRETT)
	override fun opprettEttersendingGittSkjemanr(opprettEttersendingGittSkjemaNr: OpprettEttersendingGittSkjemaNr): ResponseEntity<DokumentSoknadDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()
		combinedLogger.log(
			"Kall for å opprette ettersending på skjema ${opprettEttersendingGittSkjemaNr.skjemanr}",
			brukerId
		)

		ettersendingService.loggWarningVedEksisterendeEttersendelse(
			brukerId,
			opprettEttersendingGittSkjemaNr.skjemanr,
		)

		val arkiverteSoknader = safService.hentInnsendteSoknader(brukerId)
			.filter { opprettEttersendingGittSkjemaNr.skjemanr == it.skjemanr && it.innsendingsId != null }
			.filter { it.innsendtDato.isAfter(OffsetDateTime.now().minusDays(MAX_AKTIVE_DAGER)) }
			.sortedByDescending { it.innsendtDato }
		val innsendteSoknader =
			try {
				soknadService.hentInnsendteSoknader(tilgangskontroll.hentPersonIdents())
					.filter { it.skjemanr == opprettEttersendingGittSkjemaNr.skjemanr }
					.filter { it.innsendtDato!!.isAfter(OffsetDateTime.now().minusDays(MAX_AKTIVE_DAGER)) }
					.sortedByDescending { it.innsendtDato }
			} catch (e: Exception) {
				logger.info("Ingen søknader funnet i basen for bruker på skjemanr = ${opprettEttersendingGittSkjemaNr.skjemanr}")
				emptyList()
			}

		logger.info("Gitt skjemaNr ${opprettEttersendingGittSkjemaNr.skjemanr}: Antall innsendteSoknader=${innsendteSoknader.size} og Antall arkiverteSoknader=${arkiverteSoknader.size}")
		val dokumentSoknadDto =
			ettersendingService.opprettDokumentSoknadDto(
				innsendteSoknader = innsendteSoknader,
				arkiverteSoknader = arkiverteSoknader,
				brukerId = brukerId,
				opprettEttersendingGittSkjemaNr = opprettEttersendingGittSkjemaNr
			)

		combinedLogger.log(
			"${dokumentSoknadDto.innsendingsId}: Opprettet ettersending på skjema ${opprettEttersendingGittSkjemaNr.skjemanr}",
			brukerId
		)

		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(dokumentSoknadDto)
	}

}

