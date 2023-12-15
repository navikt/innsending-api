package no.nav.soknad.innsending.rest.fyllut

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.FyllutApi
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.PrefillService
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.supervision.timer.Timed
import no.nav.soknad.innsending.util.Constants.CLAIM_ACR_IDPORTEN_LOA_HIGH
import no.nav.soknad.innsending.util.Constants.TOKENX
import no.nav.soknad.innsending.util.logging.CombinedLogger
import no.nav.soknad.innsending.util.mapping.SkjemaDokumentSoknadTransformer
import no.nav.soknad.innsending.util.mapping.mapTilSkjemaDto
import no.nav.soknad.innsending.util.models.kanGjoreEndringer
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@ProtectedWithClaims(issuer = TOKENX, claimMap = [CLAIM_ACR_IDPORTEN_LOA_HIGH])
class FyllutRestApi(
	val restConfig: RestConfig,
	val soknadService: SoknadService,
	val tilgangskontroll: Tilgangskontroll,
	val prefillService: PrefillService
) : FyllutApi {

	private val logger = LoggerFactory.getLogger(javaClass)
	private val secureLogger = LoggerFactory.getLogger("secureLogger")
	private val combinedLogger = CombinedLogger(logger, secureLogger)

	// FIXME: Fjern dette endepunktet etter at det er byttet ut
	@Timed(InnsenderOperation.OPPRETT)
	override fun fyllUt(skjemaDto: SkjemaDto): ResponseEntity<Unit> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		combinedLogger.log(
			"Skal opprette søknad fra FyllUt: ${skjemaDto.skjemanr}, ${skjemaDto.tittel}, ${skjemaDto.tema}, ${skjemaDto.spraak}",
			brukerId
		)
		soknadService.loggWarningVedEksisterendeSoknad(brukerId, skjemaDto.skjemanr, SoknadType.soknad)

		val opprettetSoknad = soknadService.opprettNySoknad(
			SkjemaDokumentSoknadTransformer().konverterTilDokumentSoknadDto(
				skjemaDto,
				brukerId
			)
		)

		combinedLogger.log(
			"${opprettetSoknad.innsendingsId}: Soknad fra FyllUt opprettet. Antall vedlegg fra FyllUt=${skjemaDto.vedleggsListe?.size}",
			brukerId
		)

		return ResponseEntity.status(HttpStatus.FOUND)
			.location(URI.create(restConfig.sendInnUrl + "/" + opprettetSoknad.innsendingsId)).build()
	}

	@Timed(InnsenderOperation.OPPRETT)
	override fun fyllUtOpprettSoknad(
		skjemaDto: SkjemaDto,
		force: Boolean?
	): ResponseEntity<Any> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		combinedLogger.log(
			"Skal opprette søknad fra FyllUt: ${skjemaDto.skjemanr}, ${skjemaDto.tittel}, ${skjemaDto.tema}, ${skjemaDto.spraak}",
			brukerId
		)

		val redirectVedPaabegyntSoknad =
			redirectVedPaabegyntSoknad(brukerId, skjemaDto, force ?: false)
		if (redirectVedPaabegyntSoknad != null) return redirectVedPaabegyntSoknad

		val dokumentSoknadDto = SkjemaDokumentSoknadTransformer().konverterTilDokumentSoknadDto(skjemaDto, brukerId)
		val opprettetSoknad = soknadService.opprettNySoknad(dokumentSoknadDto)

		combinedLogger.log(
			"${opprettetSoknad.innsendingsId}: Soknad fra FyllUt opprettet",
			brukerId
		)

		return ResponseEntity.status(HttpStatus.CREATED).body(opprettetSoknad)
	}

	// Skal redirecte til påbegynt søknad hvis bruker har en søknad under arbeid (med mindre det er sendt inn eksplisitt paremeter for å opprette ny søknad likevel)
	private fun redirectVedPaabegyntSoknad(
		brukerId: String,
		skjemaDto: SkjemaDto,
		forceCreate: Boolean = false
	): ResponseEntity<Any>? {
		val aktiveSoknader = soknadService.hentAktiveSoknader(brukerId, skjemaDto.skjemanr)
		val harSoknadUnderArbeid = aktiveSoknader.isNotEmpty()

		if (harSoknadUnderArbeid && !forceCreate) {
			val body = BodyStatusResponseDto(
				status = ErrorCode.SOKNAD_ALREADY_EXISTS.code,
				info = "Søknad for dette skjemanummeret er allerede påbegynt. Redirect til side for å velge mellom å fortsette påbegynt søknad eller opprette ny søknad.",
			)
			return ResponseEntity.status(HttpStatus.OK).body(body)
		}
		return null
	}

	@Timed(InnsenderOperation.ENDRE)
	override fun fyllUtOppdaterSoknad(innsendingsId: String, skjemaDto: SkjemaDto): ResponseEntity<SkjemaDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		combinedLogger.log("$innsendingsId: Skal oppdatere søknad fra FyllUt", brukerId)

		val dokumentSoknadDto = SkjemaDokumentSoknadTransformer().konverterTilDokumentSoknadDto(
			skjemaDto,
			brukerId
		)

		val updatedSoknad = soknadService.updateSoknad(innsendingsId, dokumentSoknadDto)

		combinedLogger.log("$innsendingsId: Soknad fra FyllUt oppdatert", brukerId)

		return ResponseEntity.status(HttpStatus.OK).body(updatedSoknad)
	}

	@Timed(InnsenderOperation.ENDRE)
	override fun fyllUtUtfyltSoknad(innsendingsId: String, skjemaDto: SkjemaDto): ResponseEntity<Unit> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		combinedLogger.log("$innsendingsId: Skal fullføre søknad fra FyllUt", brukerId)

		val dokumentSoknadDto = SkjemaDokumentSoknadTransformer().konverterTilDokumentSoknadDto(
			skjemaDto,
			brukerId
		)

		soknadService.updateUtfyltSoknad(innsendingsId, dokumentSoknadDto)

		combinedLogger.log("$innsendingsId: Utfylt søknad fra Fyllut", brukerId)

		return ResponseEntity
			.status(HttpStatus.FOUND).location(URI.create(restConfig.sendInnUrl + "/" + innsendingsId))
			.build()
	}

	@Timed(InnsenderOperation.HENT)
	override fun fyllUtHentSoknad(innsendingsId: String): ResponseEntity<SkjemaDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()
		combinedLogger.log("$innsendingsId: Kall fra FyllUt for å hente søknad", brukerId)

		val dokumentSoknadDto = soknadService.hentSoknadMedHoveddokumentVariant(innsendingsId)
		validerSoknadsTilgang(dokumentSoknadDto)

		combinedLogger.log("$innsendingsId: Hentet søknad fra FyllUt", brukerId)

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(mapTilSkjemaDto(dokumentSoknadDto))
	}

	@Timed(InnsenderOperation.SLETT)
	override fun fyllUtSlettSoknad(innsendingsId: String): ResponseEntity<BodyStatusResponseDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()
		combinedLogger.log("$innsendingsId: Kall fra FyllUt for å slette søknad", brukerId)

		val dokumentSoknadDto = soknadService.hentSoknad(innsendingsId)
		validerSoknadsTilgang(dokumentSoknadDto)

		soknadService.slettSoknadAvBruker(dokumentSoknadDto)
		combinedLogger.log("$innsendingsId: Slettet søknad", brukerId)

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(BodyStatusResponseDto(HttpStatus.OK.name, "Slettet soknad med id $innsendingsId"))

	}

	override fun fyllUtPrefillData(properties: List<String>): ResponseEntity<PrefillData> {
		val userId = tilgangskontroll.hentBrukerFraToken()
		combinedLogger.log("Kall fra FyllUt for å hente prefill-data", userId)

		val prefillData = prefillService.getPrefillData(properties, userId)

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(prefillData)
	}

	// Used by fyllut and fyllut-ettersending
	@Timed(InnsenderOperation.HENT)
	override fun hentSoknaderForSkjemanr(
		skjemanr: String,
		soknadstyper: List<SoknadType>?
	): ResponseEntity<List<DokumentSoknadDto>> {
		val brukerIds = tilgangskontroll.hentPersonIdents()

		brukerIds.forEach {
			combinedLogger.log(
				"Kall for å hente søknader med $skjemanr for bruker. Soknadstyper=${soknadstyper ?: "ikke spesifisert"}",
				it
			)
		}

		val soknader = mutableListOf<DokumentSoknadDto>()

		if (soknadstyper.isNullOrEmpty()) {
			brukerIds.forEach { combinedLogger.log("Henter søknader med alle søknadstyper for $skjemanr", it) }
			soknader.addAll(brukerIds.flatMap { soknadService.hentAktiveSoknader(it, skjemanr) })
		}

		if (soknadstyper?.contains(SoknadType.soknad) == true) {
			brukerIds.forEach { combinedLogger.log("Henter søknader med søknadstype 'soknad' for $skjemanr", it) }
			soknader.addAll(brukerIds.flatMap { soknadService.hentAktiveSoknader(it, skjemanr, SoknadType.soknad) })
		}

		if (soknadstyper?.contains(SoknadType.ettersendelse) == true) {
			brukerIds.forEach { combinedLogger.log("Henter søknader med søknadstype 'ettersendelse' for $skjemanr", it) }
			soknader.addAll(brukerIds.flatMap { soknadService.hentAktiveSoknader(it, skjemanr, SoknadType.ettersendelse) })
		}

		return ResponseEntity.status(HttpStatus.OK).body(soknader)
	}

	private fun validerSoknadsTilgang(dokumentSoknadDto: DokumentSoknadDto) {
		tilgangskontroll.harTilgang(dokumentSoknadDto)
		if (!dokumentSoknadDto.kanGjoreEndringer) {
			throw IllegalActionException(
				message = "Søknaden kan ikke vises. Søknaden er slettet eller innsendt og kan ikke vises eller endres.",
				errorCode = ErrorCode.APPLICATION_SENT_IN_OR_DELETED
			)
		}
	}


}
