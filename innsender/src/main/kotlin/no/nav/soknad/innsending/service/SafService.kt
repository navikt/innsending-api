package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.consumerapis.saf.SafInterface
import no.nav.soknad.innsending.consumerapis.saf.dto.ArkiverteSaker
import no.nav.soknad.innsending.consumerapis.saf.dto.Dokument
import no.nav.soknad.innsending.model.AktivSakDto
import no.nav.soknad.innsending.model.InnsendtVedleggDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter


@Service
class SafService(val safApi: SafInterface) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun hentInnsendteSoknader(brukerId: String): List<AktivSakDto> {
		val innsendte: List<ArkiverteSaker> =
			try {
				safApi.hentBrukersSakerIArkivet(brukerId)
			} catch (ex: Exception) {
				logger.info("Oppslag mot SAF gav ingen resultat pga feilen ${ex.message}")
				emptyList()
			}

		logger.info("Hentet ${innsendte.size} journalposter for bruker, skal mappe til AktivSakDto")
		val innsendteMedHovedDokMedBrevkode = innsendte.filter { harHoveddokumentMedBrevkodeSatt(it.dokumenter) }
		logger.debug("innsendteMedHovedDokMedBrevkode ${innsendteMedHovedDokMedBrevkode.size}")
		return innsendteMedHovedDokMedBrevkode.map {
			AktivSakDto(finnBrevKodeForHoveddokument(it.dokumenter), it.tittel, it.tema,
				konverterTilDateTime(it.datoMottatt ?: ""), erEttersending(it.dokumenter),
				konverterTilVedleggsliste(it.dokumenter), it.eksternReferanseId ) }
	}

	private fun harHoveddokumentMedBrevkodeSatt(innsendteDokumenter: List<Dokument>): Boolean {
		return innsendteDokumenter.count { it.k_tilkn_jp_som.equals("Hoveddokument", true) && it.brevkode != null && it.brevkode.startsWith("NAV")} > 0
	}

	private fun konverterTilDateTime(dateString: String): OffsetDateTime {
		if (dateString.isBlank()) return OffsetDateTime.MIN
		val formatter: DateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME
		val dateTime = LocalDateTime.parse(dateString, formatter)
		val zoneOffSet = OffsetDateTime.now().offset
		return dateTime.atOffset(zoneOffSet)
	}

	private fun erEttersending(dokumenter: List<Dokument>): Boolean {
		val hoveddokumenter = dokumenter.filter { it.k_tilkn_jp_som.equals("Hoveddokument", true) }
		return hoveddokumenter.map { it.brevkode }.contains("NAVe")
	}

	private fun finnBrevKodeForHoveddokument(dokumenter: List<Dokument>): String {
		val hoveddokumenter = dokumenter.filter { it.k_tilkn_jp_som.equals("Hoveddokument", true) }
		return fjernEttersendingsMerkeFraSkjemanr(hoveddokumenter.map { it.brevkode }.first()!!)
	}

	private fun konverterTilVedleggsliste(dokumenter: List<Dokument>): List<InnsendtVedleggDto> {
		return dokumenter
			.filter {!it.brevkode.isNullOrBlank() && !"L7".equals(it.brevkode, true)}
			.map { InnsendtVedleggDto(vedleggsnr = fjernEttersendingsMerkeFraSkjemanr(it.brevkode!!), it.tittel) }
	}

	private fun fjernEttersendingsMerkeFraSkjemanr(brevkode: String): String {
		return brevkode.replace("NAVe", "NAV")
	}
}
