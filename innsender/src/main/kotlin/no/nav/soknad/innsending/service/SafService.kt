package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.consumerapis.saf.SafInterface
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
		val innsendte = safApi.hentBrukersSakerIArkivet(brukerId)
		if (innsendte == null) return emptyList()

		logger.info("Hentet ${innsendte.size} journalposter for bruker, skal mappe til AktivSakDto")
		return innsendte.map { AktivSakDto(finnBrevKode(it.dokumenter), it.tittel, it.tema,
				konverterTilDateTime(it.datoMottatt ?: ""), erEttersending(it.dokumenter), konverterTilVedleggsliste(it.dokumenter), it.eksternReferanseId ) }.toList()
	}

	private fun konverterTilDateTime(dateString: String): OffsetDateTime {
		if (dateString.isBlank()) return OffsetDateTime.MIN
		val formatter: DateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME
		val dateTime = LocalDateTime.parse(dateString, formatter)
		val zoneOffSet = OffsetDateTime.now().offset
		return dateTime.atOffset(zoneOffSet)
	}

	private fun erEttersending(dokumenter: List<Dokument>): Boolean {
		val hoveddokumenter = dokumenter.filter { it.k_tilkn_jp_som.equals("HOVEDDOKUMENT", true)}.toList()
		return hoveddokumenter.map { it.brevkode }.toList().contains("NAVe")
	}

	private fun finnBrevKode(dokumenter: List<Dokument>): String {
		val hoveddokumenter = dokumenter.filter { it.k_tilkn_jp_som.equals("HOVEDDOKUMENT", true)}.toList()
		return hoveddokumenter.map { it.brevkode }.first()!!.replace("NAVe", "NAV")
	}

	private fun konverterTilVedleggsliste(dokumenter: List<Dokument>): List<InnsendtVedleggDto> {
		return dokumenter.map {InnsendtVedleggDto(it.k_tilkn_jp_som, it.tittel)}.toList()
	}
}
